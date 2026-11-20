//! VR teleport movement (`HORIZON_XR_MOVE=teleport`, the launcher's Movement: Teleport): the
//! left stick pushed ahead aims along the left controller at the top of a block within
//! reach, and letting it go moves the player there.
//!
//! The move goes to the server as ordinary position packets, one leg a tick, each straight
//! along one axis through free space, so a server's own checks (moved too quickly, moved
//! wrongly, `handlePlayerPositionChange`'s collisions) find nothing to send back: up first
//! when the landing is higher, across, then down when it is lower. The last leg lands on
//! the ground at once, so a server counts no fall (as a vanilla server would for any
//! client that moves this way). A landing no leg path reaches can't be picked.
use super::Game;
use horizon_client::collisions::{SceneCollisions, no_collision_or_liquid};
use horizon_core::geometry::{Aabb, Vec3};
use std::sync::Mutex;

/// How far a teleport reaches (blocks): under a server's ten blocks a packet.
const REACH: f64 = 8.0;

/// The legs of a teleport let go of, still to be taken (feet positions), one per tick.
static LEGS: Mutex<Vec<Vec3>> = Mutex::new(Vec::new());
/// The stick is pushed ahead: a teleport is being aimed.
static AIMING: std::sync::atomic::AtomicBool = std::sync::atomic::AtomicBool::new(false);

/// Whether the left stick walks now: always without teleport movement, and with it only in
/// water (swimming) or on a ladder, where there is nothing to land on.
pub(super) fn stick_walks() -> bool {
    !horizon_xr::teleport_movement() || WALKS.load(std::sync::atomic::Ordering::Relaxed)
}
static WALKS: std::sync::atomic::AtomicBool = std::sync::atomic::AtomicBool::new(false);

impl Game<'_> {
    /// Each frame: aims a teleport while the left stick is pushed ahead, and plans its legs
    /// once the stick is let go.
    pub(super) fn xr_teleport_frame(&mut self, playing: bool) {
        if !horizon_xr::teleport_movement() {
            return;
        }
        let walks = self
            .player
            .as_ref()
            .is_some_and(|p| p.fluids.was_touching_water || p.on_climbable);
        WALKS.store(walks, std::sync::atomic::Ordering::Relaxed);
        let stick = horizon_xr::pad().map_or([0.0; 2], |p| p.left);
        let was = AIMING.load(std::sync::atomic::Ordering::Relaxed);
        // Pushed past 0.6 to aim, aiming until back under 0.3: let go of there.
        let aiming = playing && !walks && (stick[1] > 0.6 || (was && stick[1] > 0.3));
        AIMING.store(aiming, std::sync::atomic::Ordering::Relaxed);
        let target = if aiming || was {
            self.teleport_target()
        } else {
            None
        };
        let eye = self.eye_position();
        horizon_xr::set_teleport_target(if aiming {
            target.as_ref().zip(eye).map(|((at, legs), eye)| {
                let offset = glam::Vec3::new(
                    (at.x - eye[0]) as f32,
                    (at.y - eye[1]) as f32,
                    (at.z - eye[2]) as f32,
                );
                (offset, legs.is_some())
            })
        } else {
            None
        });
        // Let go of while aiming at a landing a path reaches: go.
        if was
            && !aiming
            && playing
            && let Some((_, Some(legs))) = target
        {
            *LEGS.lock().unwrap() = legs;
        }
    }

    /// Each tick, before the player moves: the next leg of a teleport.
    pub(super) fn xr_teleport_tick(&mut self) {
        let next = {
            let mut legs = LEGS.lock().unwrap();
            if legs.is_empty() {
                return;
            }
            legs.remove(0)
        };
        let Some(p) = self.player.as_mut() else {
            LEGS.lock().unwrap().clear();
            return;
        };
        p.position = next;
        p.velocity = Vec3::ZERO;
        p.fall_distance = 0.0;
        // Drawn there at once, not slid to between ticks.
        if self.lan_acting.is_none() {
            self.previous_position = next;
        }
    }

    /// Where the left controller points within reach, at the top of a block: the feet
    /// there and the legs that reach it (none when no path of free legs does).
    fn teleport_target(&self) -> Option<(Vec3, Option<Vec<Vec3>>)> {
        let p = self.player.as_ref()?;
        let eye = self.eye_position()?;
        let (yaw, pitch) = horizon_xr::hand_angles(true)?;
        let hand = horizon_xr::last_hands()
            .and_then(|h| h[horizon_xr::Hand::LeftAim as usize])
            .map_or([0.0; 3], |h| {
                [h.offset.x as f64, h.offset.y as f64, h.offset.z as f64]
            });
        let origin = [eye[0] + hand[0], eye[1] + hand[1], eye[2] + hand[2]];
        let dir = super::pick_view_vector(yaw, pitch);
        let hit = self.clip_ray(origin, dir, REACH)?;
        let at = Vec3::new(hit.point[0], hit.point[1], hit.point[2]);
        if hit.face != [0, 1, 0] {
            return Some((at, None));
        }
        let start = p.position;
        let (half, height) = (
            f64::from(p.dimensions.width) / 2.0,
            f64::from(p.dimensions.height),
        );
        let legs = self.teleport_legs(start, at, half, height);
        Some((at, legs))
    }

    /// The legs from `from` to `to` (feet), each along one axis through free space: up,
    /// across (x then z, or z then x), down.
    fn teleport_legs(&self, from: Vec3, to: Vec3, half: f64, height: f64) -> Option<Vec<Vec3>> {
        let mut scene = SceneCollisions {
            world: &self.world,
            binding: self.collision,
            subject: None,
        };
        // A leg along one axis sweeps exactly the box around the body at both ends.
        let mut free = |a: Vec3, b: Vec3| {
            let swept = Aabb::new(
                Vec3::new(a.x.min(b.x) - half, a.y.min(b.y), a.z.min(b.z) - half),
                Vec3::new(
                    a.x.max(b.x) + half,
                    a.y.max(b.y) + height,
                    a.z.max(b.z) + half,
                ),
            );
            no_collision_or_liquid(&mut scene, swept)
        };
        // Across at the higher of the two heights.
        let top = from.y.max(to.y);
        let up = Vec3::new(from.x, top, from.z);
        for x_first in [true, false] {
            let corner = if x_first {
                Vec3::new(to.x, top, from.z)
            } else {
                Vec3::new(from.x, top, to.z)
            };
            let over = Vec3::new(to.x, top, to.z);
            let path = [from, up, corner, over, to];
            if path.windows(2).all(|w| w[0] == w[1] || free(w[0], w[1])) {
                let mut legs: Vec<Vec3> = path[1..].to_vec();
                legs.dedup();
                legs.retain(|leg| *leg != from);
                return Some(legs);
            }
        }
        None
    }
}
