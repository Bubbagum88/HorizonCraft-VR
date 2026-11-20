package dev.horizonlab.horizon;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The launcher, landscape and on one screen: the skin with its picker and arm model, the
 * name, the account, a big Play button and every setting on the right.
 *
 * The name goes to horizon-player.txt (offline_name.rs: the name used on servers and LAN);
 * the skin, a PNG picked with the system file picker, to horizon-skin.png as a plain 64x64
 * or 64x32 RGBA PNG, and its arm model to horizon-skin.txt (game/local_skin.rs).
 */
public class ProfileActivity extends Activity implements View.OnClickListener {
    private static final int PICK = 7;
    private static final int PICK_DRIVER = 8;
    private static final int MS_LOGIN = 9;
    private File dir;
    private EditText name;
    private TextView message;
    private SkinView preview;
    private Button pick, model, play, driver, systemDriver, microsoft, deviceCode, signOut;
    private TextView driverStatus, accountStatus, accountChip;
    private EditText clientId;
    private LinearLayout codeBox, advancedBox;
    private TextView code, codeHint;
    private Button copy, browser, advanced, mods;
    private String verifyUri = "https://www.microsoft.com/link";
    private boolean slim;

    private static final int PANEL = Ui.CARD;
    private static final int FIELD = Color.rgb(30, 30, 32);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        dir = new File(SetupActivity.home(this));
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        int p = Ui.dp(this, 12);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackground(Ui.sunset(this));
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, pad, pad, pad);

        // Left: who plays.
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setPadding(p, p, p, p);
        left.setBackground(Ui.rounded(this, PANEL, Ui.EDGE, 16));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(this, "HORIZONCRAFT VR", 22, Ui.TEXT, true);
        title.setLetterSpacing(0.06f);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView ver = Ui.text(this, version(), 12, Ui.MUTED, false);
        head.addView(ver);
        left.addView(head);

        LinearLayout who = new LinearLayout(this);
        who.setOrientation(LinearLayout.HORIZONTAL);
        preview = new SkinView(this);
        preview.setBackground(Ui.rounded(this, FIELD, 0, 12));
        int pv = Ui.dp(this, 6);
        preview.setPadding(pv, pv, pv, pv);
        who.addView(preview, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sideLp = new LinearLayout.LayoutParams(0, -1, 1.3f);
        sideLp.leftMargin = p;
        side.addView(Ui.text(this, Ui.t("Ник", "Name"), 12, Ui.MUTED, false));
        name = new EditText(this);
        name.setSingleLine(true);
        name.setTextColor(Ui.TEXT);
        name.setHintTextColor(Ui.MUTED);
        name.setHint("Player_123");
        name.setTextSize(16);
        name.setFilters(new InputFilter[] {new InputFilter.LengthFilter(16)});
        name.setPadding(p, Ui.dp(this, 8), p, Ui.dp(this, 8));
        name.setBackground(Ui.rounded(this, FIELD, Ui.EDGE, 8));
        name.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        name.setText(readName());
        side.addView(name);
        pick = Ui.button(this, Ui.t("Скин…", "Skin…"), false);
        pick.setOnClickListener(this);
        side.addView(pick, Ui.gap(this, 8));
        slim = readModel();
        model = Ui.button(this, "", false);
        model.setOnClickListener(this);
        side.addView(model, Ui.gap(this, 6));
        accountChip = Ui.text(this, "", 12, Ui.MUTED, false);
        side.addView(accountChip, Ui.gap(this, 6));
        who.addView(side, sideLp);
        left.addView(who, new LinearLayout.LayoutParams(-1, 0, 1));

        message = Ui.text(this, "", 12, Ui.WARN, false);
        message.setMaxLines(2);
        play = Ui.button(this, Ui.t("ИГРАТЬ В VR", "PLAY VR"), true);
        play.setTextSize(22);
        play.setLetterSpacing(0.1f);
        play.setMinHeight(Ui.dp(this, 60));
        play.setOnClickListener(this);
        play.setTextColor(Color.BLACK);
        play.setShadowLayer(0, 0, 0, 0);

        // The menu's pages: each section its own scrolling page.
        LinearLayout accountBox = page(), updatesBox = page(), settingsBox = page(), modsBox = page();
        LinearLayout card = accountBox;

        Ui.section(card, Ui.t("Аккаунт Microsoft", "Microsoft account"));
        accountStatus = Ui.text(this, accountLine(), 15, Ui.TEXT, true);
        card.addView(accountStatus);
        card.addView(Ui.text(this, Ui.t(
                "Для лицензионных серверов. Войдите почтой и паролем один раз: дальше вход обновляется сам.",
                "For licensed servers. Sign in with your email and password once: it renews by itself."),
                12, Ui.MUTED, false));
        microsoft = Ui.button(this, Ui.t("Войти через Microsoft", "Sign in with Microsoft"), true);
        microsoft.setOnClickListener(this);
        card.addView(microsoft, Ui.gap(this, 8));
        codeBox = new LinearLayout(this);
        codeBox.setOrientation(LinearLayout.VERTICAL);
        codeBox.setPadding(p, p, p, p);
        codeBox.setBackground(Ui.rounded(this, FIELD, Ui.GREEN_DARK, 10));
        codeBox.setVisibility(View.GONE);
        codeHint = Ui.text(this, "", 13, Ui.MUTED, false);
        codeBox.addView(codeHint);
        code = Ui.text(this, "", 28, Ui.TEXT, true);
        code.setGravity(Gravity.CENTER);
        code.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        code.setLetterSpacing(0.12f);
        code.setTextIsSelectable(true);
        codeBox.addView(code, Ui.gap(this, 4));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        copy = Ui.button(this, Ui.t("Скопировать код", "Copy code"), false);
        copy.setOnClickListener(this);
        browser = Ui.button(this, Ui.t("Открыть браузер", "Open browser"), true);
        browser.setOnClickListener(this);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, -2, 1);
        half2.leftMargin = Ui.dp(this, 8);
        row.addView(browser, half2);
        codeBox.addView(row, Ui.gap(this, 8));
        card.addView(codeBox, Ui.gap(this, 8));
        signOut = Ui.button(this, Ui.t("Выйти из аккаунта", "Sign out"), false);
        signOut.setOnClickListener(this);
        card.addView(signOut, Ui.gap(this, 8));
        advanced = Ui.button(this, Ui.t("Другой способ входа ▾", "Other sign-in ▾"), false);
        advanced.setOnClickListener(this);
        card.addView(advanced, Ui.gap(this, 8));
        advancedBox = new LinearLayout(this);
        advancedBox.setOrientation(LinearLayout.VERTICAL);
        advancedBox.addView(Ui.text(this, Ui.t(
                "Вход по коду через свой Azure client ID, одобренный Mojang (aka.ms/mce-reviewappid). Нужен, только если вход выше не работает.",
                "Code sign-in with your own Azure client ID approved by Mojang (aka.ms/mce-reviewappid). Only needed if the sign-in above fails."),
                12, Ui.MUTED, false));
        clientId = new EditText(this);
        clientId.setSingleLine(true);
        clientId.setTextColor(Ui.TEXT);
        clientId.setHintTextColor(Ui.MUTED);
        clientId.setHint("00000000-0000-0000-0000-000000000000");
        clientId.setText(Msa.clientId(this));
        clientId.setPadding(p, p, p, p);
        clientId.setBackground(Ui.rounded(this, FIELD, Ui.EDGE, 8));
        clientId.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        advancedBox.addView(clientId, Ui.gap(this, 6));
        deviceCode = Ui.button(this, Ui.t("Войти по коду", "Sign in with a code"), false);
        deviceCode.setOnClickListener(this);
        advancedBox.addView(deviceCode, Ui.gap(this, 6));
        advancedBox.setVisibility(View.GONE);
        card.addView(advancedBox, Ui.gap(this, 6));

        // Horizon VR's own settings and updates.
        card = updatesBox;
        Ui.section(card, Ui.t("Обновления", "Updates"));
        final TextView updateStatus = Ui.text(this, "HorizonCraft VR v" + Updater.installed(this)
                + Ui.t(" · проверка…", " · checking…"), 14, Ui.TEXT, true);
        card.addView(updateStatus);
        final Button update = Ui.button(this, "", true);
        update.setVisibility(View.GONE);
        card.addView(update, Ui.gap(this, 6));
        Updater.check(this, new Updater.Found() {
            public void found(final Updater.Release release) {
                if (release == null) {
                    updateStatus.setText("HorizonCraft VR v" + Updater.installed(ProfileActivity.this)
                            + Ui.t(" · последняя версия", " · up to date"));
                    return;
                }
                updateStatus.setText(Ui.t("Доступна новая версия ", "New version available: ") + release.version);
                update.setText(Ui.t("Обновить до ", "Update to ") + release.version);
                update.setVisibility(View.VISIBLE);
                update.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        update.setEnabled(false);
                        Updater.install(ProfileActivity.this, release, new Updater.Progress() {
                            public void status(String text, boolean finished) {
                                update.setText(text);
                                if (finished) update.setEnabled(true);
                            }
                        });
                    }
                });
            }
        });

        card = settingsBox;
        Ui.section(card, Ui.t("Настройки VR", "VR settings"));
        card.addView(Ui.text(this, Ui.t(
                "Резкая картинка и столько чанков, сколько игра рисует плавно.",
                "A sharp image, with as many chunks as the game draws smoothly."),
                12, Ui.MUTED, false));
        final Button paceButton = Ui.button(this, "", false);
        final Button msaaButton = Ui.button(this, "", false);
        final Button scaleButton = Ui.button(this, "", false);
        final Button boostButton = Ui.button(this, "", false);
        final Button turnButton = Ui.button(this, "", false);
        final Button moveButton = Ui.button(this, "", false);
        final Button layoutButton = Ui.button(this, "", false);
        final boolean[] pace = {vrSetting("pace", 1) != 0};
        turnStep = (int) vrSetting("turn", 0);
        teleportOn = vrSetting("teleport", 0) != 0;
        questcraftLayout = vrSetting("layout", 0) == 1;
        msaaOn = vrSetting("msaa", 1) != 0;
        boostOn = vrSetting("boost", 1) != 0;
        vrScale = vrSetting("scale", 0);
        // One mode, Quality: a sharp image with the chunks it can draw smoothly. Applied once
        // (the game's options can still be changed in the game afterwards).
        // With MSAA the edges are smooth at 1.0x: 1.4x on top of it was four times the samples
        // of 1.0x, and the game fell to a third of 90 Hz.
        // (`preset`: the defaults' version; an older file takes the new ones once.)
        if (!"quality".equals(vrMode()) || vrSetting("preset", 0) < 2) {
            vrScale = qualityScale();
            applyMode("quality", pace[0]);
        }
        final TextView summary = Ui.text(this, "", 13, Ui.TEXT, true);
        final Runnable showVr = new Runnable() {
            public void run() {
                paceButton.setText(Ui.t("Ровные кадры: ", "Frame pacing: ") + (pace[0] ? "Auto" : "Off"));
                msaaButton.setText("Antialiasing MSAA: " + (msaaOn ? "4x" : "Off"));
                scaleButton.setText(String.format(java.util.Locale.ROOT, Ui.t("Разрешение: %.2fx", "Resolution: %.2fx"), vrScale));
                boostButton.setText(Ui.t("Ускорение: ", "Performance boost: ") + (boostOn ? "On" : "Off"));
                turnButton.setText(Ui.t("Поворот: ", "Turning: ") + (turnStep > 0
                        ? String.format(java.util.Locale.ROOT, Ui.t("рывком %d°", "Snap %d°"), turnStep)
                        : Ui.t("плавный", "Smooth")));
                moveButton.setText(Ui.t("Движение: ", "Movement: ") + (teleportOn
                        ? Ui.t("телепорт", "Teleport") : Ui.t("ходьба", "Walk")));
                layoutButton.setText(Ui.t("Кнопки: ", "Controls: ") + (questcraftLayout ? "QuestCraft" : "Horizon"));
                summary.setText(String.format(java.util.Locale.ROOT,
                        "Quality · %.2fx · 90 Hz · 6 chunks · anisotropic 8x%s", vrScale, msaaOn ? " · MSAA 4x" : ""));
            }
        };
        showVr.run();
        paceButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pace[0] = !pace[0];
                saveVr(vrScale, 90, pace[0]);
                showVr.run();
            }
        });
        card.addView(summary);
        msaaButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                msaaOn = !msaaOn;
                vrScale = qualityScale();
                saveVr(vrScale, 90, pace[0]);
                showVr.run();
            }
        });
        scaleButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // The next step up, then round again: sharper costs frames. 1.0x without MSAA
                // too, for a headset whose GPU can't fill 1.2x (the Quest 1's).
                float[] steps = msaaOn ? new float[] {1.0f, 1.15f, 1.3f} : new float[] {1.0f, 1.2f, 1.4f};
                float next = steps[0];
                for (float step : steps) {
                    if (step > vrScale + 0.01f) {
                        next = step;
                        break;
                    }
                }
                vrScale = next;
                saveVr(vrScale, 90, pace[0]);
                showVr.run();
            }
        });
        boostButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boostOn = !boostOn;
                saveVr(vrScale, 90, pace[0]);
                showVr.run();
            }
        });
        turnButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Smooth, then snap turns of 30, 45 and 90 degrees, then round again.
                turnStep = turnStep == 0 ? 30 : turnStep == 30 ? 45 : turnStep == 45 ? 90 : 0;
                saveVr(vrScale, 90, pace[0]);
                showVr.run();
            }
        });
        moveButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                teleportOn = !teleportOn;
                saveVr(vrScale, 90, pace[0]);
                showVr.run();
            }
        });
        layoutButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                questcraftLayout = !questcraftLayout;
                saveVr(vrScale, 90, pace[0]);
                showVr.run();
            }
        });
        card.addView(scaleButton, Ui.gap(this, 6));
        card.addView(Ui.text(this, Ui.t(
                "Разрешение: выше — резче, но меньше кадров.",
                "Resolution: higher is sharper, but draws fewer frames. Try a step up or down to find what your eyes prefer."),
                12, Ui.MUTED, false));
        card.addView(boostButton, Ui.gap(this, 6));
        card.addView(Ui.text(this, Ui.t(
                "Ускорение: процессор и графика шлема на максимуме. Больше кадров, шлем теплее, батарея садится быстрее.",
                "Performance boost: the headset's CPU and GPU at their highest clocks. More frames; the headset runs warmer and the battery drains faster."),
                12, Ui.MUTED, false));
        card.addView(turnButton, Ui.gap(this, 6));
        card.addView(Ui.text(this, Ui.t(
                "Поворот правым стиком: плавно или рывком на заданный угол.",
                "Turning with the right stick: smoothly, or in snap steps at once (easier on the stomach)."),
                12, Ui.MUTED, false));
        card.addView(moveButton, Ui.gap(this, 6));
        card.addView(Ui.text(this, Ui.t(
                "Телепорт: левый стик вперёд — прицел левым контроллером на блок до 8 блоков, отпустить — переместиться. В воде и на лестницах стик ходит.",
                "Teleport: push the left stick ahead and point the left controller at the top of a block up to 8 blocks away, let go to move there. In water and on ladders the stick walks."),
                12, Ui.MUTED, false));
        card.addView(layoutButton, Ui.gap(this, 6));
        card.addView(Ui.text(this, Ui.t(
                "QuestCraft: A прыжок, Y инвентарь, X пауза, левый хват присесть, правый хват бег, правый стик вверх/вниз слот, B один предмет в инвентаре.",
                "QuestCraft: A jumps, Y inventory, X pause, hold left grip to crouch, hold right grip to sprint, right stick up/down changes slot, B puts one item down in an inventory."),
                12, Ui.MUTED, false));
        card.addView(paceButton, Ui.gap(this, 6));
        card.addView(msaaButton, Ui.gap(this, 6));
        card.addView(Ui.text(this, Ui.t(
                "MSAA сглаживает края блоков вдали. Если игра не запускается или тормозит, выключите его.",
                "MSAA smooths the shimmering edges of far blocks. If the game does not start or runs slowly, turn it off."),
                12, Ui.MUTED, false));
        card.addView(Ui.text(this, Ui.t(
                "Ровные кадры: если игра не успевает, она рисует каждый второй кадр ровно, без рывков.",
                "Frame pacing: when the game cannot keep up, it draws every second frame evenly instead of unevenly, without micro-stutter."),
                12, Ui.MUTED, false));

        // Horizon VR: no Vulkan driver choice (the headset's runtime always uses the system's)
        // and no screen cutout setting (the headset has no cutout).
        card = modsBox;
        Ui.section(card, Ui.t("Моды", "Mods"));
        card.addView(Ui.text(this, Ui.t(
                ".hnmod со сборкой для Android или .hmod. Или положите файл в Android/data/dev.horizonlab.horizon/files/mods/.",
                "A .hnmod with an Android build, or a .hmod. Or drop the file into Android/data/dev.horizonlab.horizon/files/mods/."),
                12, Ui.MUTED, false));
        mods = Ui.button(this, Ui.t("Установить мод", "Install a mod"), false);
        mods.setOnClickListener(this);
        card.addView(mods, Ui.gap(this, 8));

        // A launcher's classic layout: a menu down the left that folds to its icons, and the
        // pages beside it. The home page is the night-sky picture with Play in a bar below.
        root.setPadding(0, 0, 0, 0);
        // Stone bricks behind the pages, smooth slabs for the menu and the Play bar.
        root.setBackground(Ui.stone(this, true, 26));
        final FrameLayout content = new FrameLayout(this);
        LinearLayout home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        FrameLayout hero = new FrameLayout(this);
        hero.setBackground(Ui.sunset(this));
        TextView brand = Ui.text(this, "HORIZONCRAFT VR", 34, Ui.TEXT, true);
        brand.setLetterSpacing(0.1f);
        brand.setGravity(Gravity.CENTER);
        hero.addView(brand, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
        ((FrameLayout.LayoutParams) brand.getLayoutParams()).topMargin = Ui.dp(this, 40);
        home.addView(hero, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(Ui.framed(this, Ui.stone(this, false, 20), 1));
        bar.setPadding(Ui.dp(this, 20), Ui.dp(this, 10), Ui.dp(this, 20), Ui.dp(this, 10));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.addView(Ui.text(this, "HorizonCraft VR " + version(), 14, Ui.TEXT, true));
        info.addView(message);
        bar.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        bar.addView(play, new LinearLayout.LayoutParams(Ui.dp(this, 260), Ui.dp(this, 64)));
        home.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        left.setPadding(Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24));
        left.setBackground(Ui.rounded(this, Ui.CARD, Ui.EDGE, 0));
        final View[] pages = {home, left, wrap(settingsBox), wrap(accountBox), wrap(updatesBox), wrap(modsBox)};
        final String[][] items = {
            {"\u2302", Ui.t("Главная", "Home")},
            // Skins: a coat hanger (drawn, `hanger`; no character has one).
            {"\uFFFC", Ui.t("Скин", "Skin")},
            {"\u2699", Ui.t("Настройки", "Settings")},
            {"\u2709", Ui.t("Аккаунт", "Account")},
            {"\u21BB", Ui.t("Обновления", "Updates")},
            {"\u2726", Ui.t("Моды", "Mods")},
        };
        for (View page : pages) content.addView(page, new FrameLayout.LayoutParams(-1, -1));
        final LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setBackground(Ui.framed(this, Ui.stone(this, false, 15), 1));
        menu.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        final Button fold = Ui.button(this, "\u2630", false);
        menu.addView(fold, new LinearLayout.LayoutParams(-1, Ui.dp(this, 52)));
        final Button[] entries = new Button[items.length];
        final boolean[] open = {false};
        final int[] shown = {0};
        final Runnable refresh = new Runnable() {
            public void run() {
                for (int i = 0; i < pages.length; i++) {
                    pages[i].setVisibility(i == shown[0] ? View.VISIBLE : View.GONE);
                    String label = open[0] ? items[i][0] + "   " + items[i][1] : items[i][0];
                    if (i == 1) {
                        android.text.SpannableString text = new android.text.SpannableString(label);
                        text.setSpan(new android.text.style.ImageSpan(hanger(i == shown[0] ? Color.BLACK : Color.WHITE),
                                android.text.style.DynamicDrawableSpan.ALIGN_BASELINE), 0, 1, 0);
                        entries[i].setText(text);
                    } else {
                        entries[i].setText(label);
                    }
                    entries[i].setGravity(open[0] ? Gravity.CENTER_VERTICAL | Gravity.START : Gravity.CENTER);
                    entries[i].setTextColor(i == shown[0] ? Color.BLACK : Color.WHITE);
                    entries[i].setBackground(i == shown[0]
                            ? new Ui.Bevel(Ui.dp(ProfileActivity.this, 2), Color.WHITE, Ui.EDGE, false)
                            : new Ui.Bevel(Ui.dp(ProfileActivity.this, 2), Ui.STONE, Ui.EDGE, false));
                }
                menu.getLayoutParams().width = Ui.dp(ProfileActivity.this, open[0] ? 210 : 68);
                menu.requestLayout();
            }
        };
        for (int i = 0; i < items.length; i++) {
            final int index = i;
            entries[i] = Ui.button(this, items[i][0], false);
            entries[i].setTextSize(18);
            entries[i].setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    shown[0] = index;
                    refresh.run();
                }
            });
            menu.addView(entries[i], new LinearLayout.LayoutParams(-1, Ui.dp(this, 52)));
            ((LinearLayout.LayoutParams) entries[i].getLayoutParams()).topMargin = Ui.dp(this, 6);
        }
        fold.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                open[0] = !open[0];
                refresh.run();
            }
        });
        root.addView(menu, new LinearLayout.LayoutParams(Ui.dp(this, 68), -1));
        root.addView(content, new LinearLayout.LayoutParams(0, -1, 1));
        refresh.run();
        setContentView(root);
        hideBars();
        refreshAccount();
        updateModel();
        showPreview();
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) hideBars();
    }

    private void hideBars() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(android.view.WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
    }

    /** A page of the menu: a padded column, scrolled by `wrap`. */
    private LinearLayout page() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 24), Ui.dp(this, 8), Ui.dp(this, 24), Ui.dp(this, 24));
        return box;
    }

    private View wrap(LinearLayout box) {
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        // A dark panel over the stone bricks, so the text reads.
        box.setBackground(Ui.rounded(this, Ui.CARD, Ui.EDGE, 0));
        FrameLayout frame = new FrameLayout(this);
        int m = Ui.dp(this, 20);
        frame.setPadding(m, m, m, m);
        frame.addView(box, new FrameLayout.LayoutParams(-1, -2));
        scroll.addView(frame);
        return scroll;
    }

    /** A coat hanger in `color`, the size of the menu's 18sp glyphs: the skin section's icon. */
    private android.graphics.drawable.Drawable hanger(final int color) {
        final int size = Ui.dp(this, 22);
        android.graphics.drawable.Drawable d = new android.graphics.drawable.Drawable() {
            public void draw(android.graphics.Canvas c) {
                android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                p.setColor(color);
                p.setStyle(android.graphics.Paint.Style.STROKE);
                p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                p.setStrokeJoin(android.graphics.Paint.Join.ROUND);
                float u = size / 24f;
                p.setStrokeWidth(2.2f * u);
                android.graphics.Path path = new android.graphics.Path();
                // The hook: from the neck up round to its tip.
                path.moveTo(12 * u, 10 * u);
                path.lineTo(12 * u, 8.5f * u);
                path.arcTo(new android.graphics.RectF(9.5f * u, 3 * u, 14.5f * u, 8 * u), 90, -270);
                // The shoulders and the bar.
                path.moveTo(12 * u, 10 * u);
                path.lineTo(2 * u, 18 * u);
                path.lineTo(22 * u, 18 * u);
                path.close();
                c.drawPath(path, p);
            }
            public void setAlpha(int alpha) {}
            public void setColorFilter(android.graphics.ColorFilter filter) {}
            public int getOpacity() {
                return android.graphics.PixelFormat.TRANSLUCENT;
            }
        };
        d.setBounds(0, 0, size, size);
        return d;
    }

    /** horizon-vr.txt in the game folder: the VR settings the game reads at start (android_main). */
    private File vrFile() {
        return new File(SetupActivity.home(this), "horizon-vr.txt");
    }

    private float vrSetting(String key, float fallback) {
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(vrFile()))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] kv = line.split("=", 2);
                if (kv.length == 2 && kv[0].trim().equals(key)) return Float.parseFloat(kv[1].trim());
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    /** The mode saved in horizon-vr.txt, or null before one was chosen. */
    private String vrMode() {
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(vrFile()))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("mode=")) return line.substring(5).trim();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * A mode: the headset's resolution and refresh rate (horizon-vr.txt, read by the game at
     * start) and the game's heaviest options (options.txt in the game folder).
     */
    private void applyMode(String mode, boolean pace) {
        float scale;
        int hz, distance, simulation, biome;
        String clouds, particles, entities;
        // Anisotropic filtering keeps far, slanted block faces sharp (textureFiltering 2).
        int filtering = mode.equals("performance") ? 0 : 2;
        int anisotropy = mode.equals("quality") ? 3 : 2;
        // All three play smoothly at 72 Hz: Quality spends the GPU on a sharper image and keeps
        // fewer chunks, Performance spends it on chunks with a softer image, Balanced sits between.
        if (mode.equals("performance")) {
            scale = 0.8f; hz = 72; distance = 12; simulation = 8; clouds = "\"false\""; particles = "1"; biome = 1; entities = "1.0";
        } else if (mode.equals("quality")) {
            scale = vrScale; hz = 90; distance = 6; simulation = 6; clouds = "\"true\""; particles = "0"; biome = 3; entities = "1.0";
        } else {
            scale = 1.0f; hz = 72; distance = 9; simulation = 8; clouds = "\"fast\""; particles = "0"; biome = 2; entities = "1.0";
        }
        try (java.io.FileWriter w = new java.io.FileWriter(vrFile())) {
            w.write(String.format(java.util.Locale.ROOT, "mode=%s\nscale=%.2f\nhz=%d\npace=%d\nmsaa=%d\nboost=%d\nturn=%d\nteleport=%d\nlayout=%d\npreset=2\n", mode, scale, hz, pace ? 1 : 0, msaaOn ? 1 : 0, boostOn ? 1 : 0, turnStep, teleportOn ? 1 : 0, questcraftLayout ? 1 : 0));
        } catch (Exception ignored) {
        }
        java.util.LinkedHashMap<String, String> set = new java.util.LinkedHashMap<>();
        set.put("renderDistance", String.valueOf(distance));
        set.put("simulationDistance", String.valueOf(simulation));
        set.put("renderClouds", clouds);
        set.put("particles", particles);
        set.put("biomeBlendRadius", String.valueOf(biome));
        set.put("entityDistanceScaling", entities);
        set.put("mipmapLevels", "4");
        set.put("textureFiltering", String.valueOf(filtering));
        set.put("maxAnisotropyBit", String.valueOf(anisotropy));
        File options = new File(SetupActivity.home(this), "options.txt");
        java.util.List<String> lines = new java.util.ArrayList<>();
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(options))) {
            String line;
            while ((line = r.readLine()) != null) lines.add(line);
        } catch (Exception ignored) {
        }
        for (java.util.Map.Entry<String, String> e : set.entrySet()) {
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith(e.getKey() + ":")) {
                    lines.set(i, e.getKey() + ":" + e.getValue());
                    found = true;
                }
            }
            if (!found) lines.add(e.getKey() + ":" + e.getValue());
        }
        try (java.io.FileWriter w = new java.io.FileWriter(options)) {
            for (String line : lines) w.write(line + "\n");
        } catch (Exception ignored) {
        }
    }

    /** VR's MSAA (horizon-vr.txt `msaa=`), on by default. */
    private boolean msaaOn = true;

    /** The headset's CPU and GPU at their highest clocks (horizon-vr.txt `boost=`). */
    private boolean boostOn = true;
    /** Snap turning's step in degrees, 0 for smooth turning (horizon-vr.txt `turn=`). */
    private int turnStep = 0;
    /** The left stick teleports instead of walking (horizon-vr.txt `teleport=`). */
    private boolean teleportOn = false;
    /** QuestCraft's button layout instead of Horizon's (horizon-vr.txt `layout=1`). */
    private boolean questcraftLayout = false;
    /** The eyes' resolution (horizon-vr.txt `scale=`). */
    private float vrScale = 1.15f;

    /** Quality's resolution to start from: 1.15x with MSAA, 1.4x without. */
    private float qualityScale() {
        return msaaOn ? 1.15f : 1.4f;
    }

    private void saveVr(float scale, int hz, boolean pace) {
        try (java.io.FileWriter w = new java.io.FileWriter(vrFile())) {
            w.write(String.format(java.util.Locale.ROOT, "mode=quality\nscale=%.2f\nhz=%d\npace=%d\nmsaa=%d\nboost=%d\nturn=%d\nteleport=%d\nlayout=%d\npreset=2\n", scale, hz, pace ? 1 : 0, msaaOn ? 1 : 0, boostOn ? 1 : 0, turnStep, teleportOn ? 1 : 0, questcraftLayout ? 1 : 0));
        } catch (Exception ignored) {
        }
    }

    private String version() {
        try {
            return "v" + getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    private String readName() {
        try {
            for (String l : new String(Files.readAllBytes(new File(dir, "horizon-player.txt").toPath()), StandardCharsets.UTF_8).split("\n")) {
                if (l.startsWith("name:")) return l.substring(5).trim();
            }
        } catch (Exception e) {
            // No name yet: the game makes one.
        }
        return "";
    }

    private boolean readModel() {
        try {
            return new String(Files.readAllBytes(new File(dir, "horizon-skin.txt").toPath()), StandardCharsets.UTF_8).contains("model:slim");
        } catch (Exception e) {
            return false;
        }
    }

    private void updateModel() {
        model.setText(Ui.t("Руки: ", "Arms: ") + (slim ? Ui.t("тонкие (Alex)", "slim (Alex)") : Ui.t("обычные (Steve)", "classic (Steve)")));
    }

    static boolean validName(String n) {
        return n.matches("[A-Za-z0-9_]{3,16}");
    }

    private void refreshAccount() {
        String n = Msa.signedIn(this);
        accountStatus.setText(accountLine());
        signOut.setVisibility(n.isEmpty() ? View.GONE : View.VISIBLE);
        microsoft.setVisibility(n.isEmpty() ? View.VISIBLE : View.GONE);
        accountChip.setText(n.isEmpty() ? Ui.t("● Без лицензии (офлайн)", "● Offline (no license)") : Ui.t("● Лицензия: ", "● Licensed: ") + n);
        accountChip.setTextColor(n.isEmpty() ? Ui.MUTED : Ui.GREEN);
    }

    /** Saves the name and arm model; false (with a message) for a bad name. */
    private boolean saveProfile() {
        String n = name.getText().toString().trim();
        if (!n.isEmpty() && !validName(n)) {
            message.setTextColor(Ui.WARN);
            message.setText(Ui.t("Ник: 3–16 символов, только латиница, цифры и _.", "Name: 3–16 characters, letters, digits and _ only."));
            return false;
        }
        try {
            if (!n.isEmpty() && !n.equals(readName())) {
                write(new File(dir, "horizon-player.txt"), ("name:" + n + "\n").getBytes(StandardCharsets.UTF_8));
            }
            saveModel();
            return true;
        } catch (Exception e) {
            message.setText(Ui.t("Не удалось сохранить.", "Could not save."));
            return false;
        }
    }

    private void saveModel() throws Exception {
        if (readModel() == slim && new File(dir, "horizon-skin.txt").isFile()) return;
        write(new File(dir, "horizon-skin.txt"), ("model:" + (slim ? "slim" : "classic") + "\n").getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Leaving for the game or the file picker keeps what was typed.
        String n = name.getText().toString().trim();
        if (n.isEmpty() || validName(n)) saveProfile();
    }

    public void onClick(View v) {
        if (v == pick) {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
            startActivityForResult(i, PICK);
        } else if (v == model) {
            slim = !slim;
            updateModel();
            showPreview();
            try {
                saveModel();
            } catch (Exception ignored) {
            }
        } else if (v == play) {
            if (!saveProfile()) return;
            // Left by an older build's "PLAY 2D": the game would start without VR.
            new java.io.File(getFilesDir(), "launch-2d").delete();
            Intent i = new Intent(this, HorizonActivity.class);
            // A fresh task of its own (the manifest's taskAffinity) on the headset's main display.
            // Started from this 2D panel, Quest put the game's window on the panel's virtual
            // display (Quest 1 log: "Display #1085"), even in a new task, and the VR session
            // never got focus: flat, no controllers. Started on display 0 (as `am start` from
            // the shell does) it gets focus. As github.com/amwatson/2DVrHybrid does.
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            android.app.ActivityOptions options = android.app.ActivityOptions.makeBasic();
            options.setLaunchDisplayId(android.view.Display.DEFAULT_DISPLAY);
            startActivity(i, options.toBundle());
            finish();
        } else if (v == driver) {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, PICK_DRIVER);
        } else if (v == systemDriver) {
            Turnip.useSystem(this);
            driverStatus.setText(Turnip.status(this));
            message.setTextColor(Ui.GREEN);
            message.setText(Ui.t("Системный драйвер. Перезапустите игру.", "System driver. Restart the game."));
        } else if (v == copy) {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("code", code.getText()));
            copy.setText(Ui.t("Скопировано ✓", "Copied ✓"));
        } else if (v == browser) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(verifyUri)));
            } catch (Exception e) {
                message.setText(Ui.t("Нет браузера. Откройте ", "No browser. Open ") + verifyUri);
            }
        } else if (v == advanced) {
            advancedBox.setVisibility(advancedBox.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        } else if (v == microsoft) {
            try {
                startActivityForResult(new Intent(this, MsaLoginActivity.class), MS_LOGIN);
            } catch (Exception e) {
                advancedBox.setVisibility(View.VISIBLE);
                message.setTextColor(Ui.WARN);
                message.setText(Ui.t("Нет WebView на телефоне: войдите по коду ниже.", "No WebView on this phone: sign in with a code below."));
            }
        } else if (v == deviceCode) {
            startDeviceCode();
        } else if (v == signOut) {
            Msa.cancel();
            Msa.signOut(this);
            MsaLoginActivity.forgetCookies();
            codeBox.setVisibility(View.GONE);
            microsoft.setEnabled(true);
            deviceCode.setEnabled(true);
            refreshAccount();
            message.setTextColor(Ui.GREEN);
            message.setText(Ui.t("Вышли из аккаунта.", "Signed out."));
        } else if (v == mods) {
            startActivity(new Intent(this, ModImportActivity.class));
        }
    }

    private final Msa.Listener listener = new Msa.Listener() {
        public void onCode(String userCode, String uri) {
            verifyUri = uri;
            code.setText(userCode);
            codeHint.setText(Ui.t("Откройте ", "Open ") + uri.replace("https://", "")
                    + Ui.t(", введите код и войдите. Этот экран сам поймёт, что вы вошли.",
                    ", enter the code and sign in. This screen notices by itself."));
            copy.setText(Ui.t("Скопировать код", "Copy code"));
            codeBox.setVisibility(View.VISIBLE);
            deviceCode.setText(Ui.t("Ждём вход…", "Waiting for sign-in…"));
            message.setText("");
        }

        public void onDone(String n) {
            codeBox.setVisibility(View.GONE);
            resetButtons();
            refreshAccount();
            message.setTextColor(Ui.GREEN);
            message.setText(Ui.t("Вход выполнен: ", "Signed in: ") + n);
        }

        public void onError(String error) {
            codeBox.setVisibility(View.GONE);
            resetButtons();
            message.setTextColor(Ui.WARN);
            message.setText(error);
        }
    };

    private void resetButtons() {
        microsoft.setText(Ui.t("Войти через Microsoft", "Sign in with Microsoft"));
        microsoft.setEnabled(true);
        deviceCode.setText(Ui.t("Войти по коду", "Sign in with a code"));
        deviceCode.setEnabled(true);
    }

    private void startDeviceCode() {
        String id = clientId.getText().toString().trim();
        if (!id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            message.setTextColor(Ui.WARN);
            message.setText(Ui.t("Вставьте свой Azure client ID.", "Paste your own Azure client ID."));
            return;
        }
        try {
            Msa.saveClientId(this, id);
        } catch (Exception e) {
            message.setText(Ui.t("Не удалось сохранить client ID.", "Could not save the client ID."));
            return;
        }
        message.setTextColor(Ui.WARN);
        message.setText(Ui.t("Запрашиваем код…", "Requesting a code…"));
        deviceCode.setEnabled(false);
        microsoft.setEnabled(false);
        Msa.start(this, id, listener);
    }

    private String cutoutMode() {
        try {
            return new String(Files.readAllBytes(new File(getFilesDir(), "cutout.txt").toPath()), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "short";
        }
    }

    private String cutoutLabel() {
        String m = cutoutMode();
        if (m.equals("never")) return Ui.t("Вырез: не заходить под камеру", "Cutout: stay clear of the camera");
        if (m.equals("always")) return Ui.t("Вырез: всегда под камеру", "Cutout: draw under the camera");
        return Ui.t("Вырез: по краям экрана", "Cutout: short edges");
    }

    private String accountLine() {
        String n = Msa.signedIn(this);
        if (n.isEmpty()) return Ui.t("Не подключён", "Not connected");
        return Ui.t("Аккаунт: ", "Account: ") + n;
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == MS_LOGIN) {
            String c = data == null ? null : data.getStringExtra("code");
            if (result != RESULT_OK || c == null) {
                String err = data == null ? null : data.getStringExtra("error");
                if (err != null && !err.isEmpty()) {
                    message.setTextColor(Ui.WARN);
                    message.setText(Ui.t("Вход отменён: ", "Sign-in cancelled: ") + err);
                }
                return;
            }
            message.setTextColor(Ui.WARN);
            message.setText(Ui.t("Входим в Xbox и Minecraft…", "Signing in to Xbox and Minecraft…"));
            microsoft.setEnabled(false);
            microsoft.setText(Ui.t("Входим…", "Signing in…"));
            Msa.startWithCode(this, c, listener);
            return;
        }
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (request == PICK_DRIVER) {
            message.setTextColor(Ui.WARN);
            try {
                String n = Turnip.install(this, data.getData());
                driverStatus.setText(Turnip.status(this));
                message.setTextColor(Ui.GREEN);
                message.setText(Ui.t("Драйвер установлен: ", "Driver installed: ") + n
                        + Ui.t(". Полностью закройте игру и откройте снова.", ". Fully close the game and open it again."));
            } catch (Exception e) {
                message.setText(Ui.t("В архиве нет .so драйвера (нужен zip с libvulkan_freedreno.so).",
                        "The archive has no driver .so (a zip with libvulkan_freedreno.so is needed)."));
            }
            return;
        }
        if (request != PICK) return;
        message.setTextColor(Ui.WARN);
        try (InputStream in = getContentResolver().openInputStream(data.getData())) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > 4 << 20) throw new Exception("too big");
            }
            byte[] png = normalizeSkin(out.toByteArray());
            if (png == null) {
                message.setText(Ui.t("Нужен скин: PNG 64×64 или 64×32 (HD 128/256… тоже подойдёт).",
                        "A skin is needed: a 64×64 or 64×32 PNG (HD 128/256… works too)."));
                return;
            }
            write(new File(dir, "horizon-skin.png"), png);
            saveModel();
            message.setTextColor(Ui.GREEN);
            message.setText(Ui.t("Скин применён.", "Skin applied."));
            showPreview();
        } catch (Exception e) {
            message.setText(Ui.t("Не удалось прочитать файл.", "Could not read the file."));
        }
    }

    /**
     * The picked image as the plain PNG the game reads: 8-bit RGBA, 64x64 or 64x32 (an HD skin
     * is scaled down pixel for pixel), and a legacy skin's fully opaque hat made see-through
     * (vanilla's SkinTextureDownloader.doNotchTransparencyHack). Null when it is no skin.
     */
    static byte[] normalizeSkin(byte[] bytes) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        o.inPremultiplied = false;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
        if (b == null) return null;
        int w = b.getWidth(), h = b.getHeight();
        if (w < 64 || w > 1024 || w % 64 != 0 || (h != w && h * 2 != w)) return null;
        if (b.getConfig() != Bitmap.Config.ARGB_8888) b = b.copy(Bitmap.Config.ARGB_8888, true);
        if (w != 64) b = Bitmap.createScaledBitmap(b, 64, h * 64 / w, false);
        int[] px = new int[64 * b.getHeight()];
        b.getPixels(px, 0, 64, 0, 0, 64, b.getHeight());
        if (b.getHeight() == 32) {
            boolean opaque = true;
            for (int y = 0; y < 16 && opaque; y++) {
                for (int x = 32; x < 64; x++) {
                    if ((px[y * 64 + x] >>> 24) < 128) {
                        opaque = false;
                        break;
                    }
                }
            }
            if (opaque) for (int y = 0; y < 16; y++) for (int x = 32; x < 64; x++) px[y * 64 + x] = 0;
        }
        Bitmap clean = Bitmap.createBitmap(64, b.getHeight(), Bitmap.Config.ARGB_8888);
        clean.setPremultiplied(false);
        clean.setPixels(px, 0, 64, 0, 0, 64, b.getHeight());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!clean.compress(Bitmap.CompressFormat.PNG, 100, out)) return null;
        return out.toByteArray();
    }

    private static void write(File f, byte[] bytes) throws Exception {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(bytes);
        }
        if (!tmp.renameTo(f)) {
            f.delete();
            if (!tmp.renameTo(f)) throw new Exception("rename");
        }
    }

    private void showPreview() {
        File f = new File(dir, "horizon-skin.png");
        Bitmap skin = null;
        if (f.isFile()) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            skin = BitmapFactory.decodeFile(f.getPath(), o);
            if (skin != null && (skin.getWidth() != 64 || (skin.getHeight() != 64 && skin.getHeight() != 32))) {
                // An older file the game cannot use either: clean it like a new pick.
                try {
                    byte[] png = normalizeSkin(Files.readAllBytes(f.toPath()));
                    if (png != null) {
                        write(f, png);
                        skin = BitmapFactory.decodeByteArray(png, 0, png.length, o);
                    } else {
                        skin = null;
                    }
                } catch (Exception e) {
                    skin = null;
                }
            }
        }
        preview.set(skin, slim);
    }
}
