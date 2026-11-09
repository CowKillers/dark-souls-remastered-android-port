package org.nx2a.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Launcher Dark Souls Remastered (nx2android, v1 Turnip).
 * Adaptado do launcher dark-turnip do Ninja Gaiden Sigma 2 (padrao que
 * funciona no S23), SEM copiar arte, links ou mapeamento daquele jogo.
 *  - Host rebuildado do nxnative DESTE zip, com SOMENTE o patch
 *    adrenotools (DM_VK_DRIVER): sem ele, qualquer botao de driver
 *    seria inerte, porque o host original so abre libvulkan.so.
 *    Sem DM_VK_DRIVER, o caminho original do sistema fica intacto.
 *    Se o Turnip falhar, o host volta sozinho ao Sistema e loga
 *    o fallback ([RED] [vk] ... falling back to system driver).
 *  - Driver padrao = Sistema. Turnips inclusos: v26.3.0-R6 (a7xx/a6xx,
 *    o certo para o Adreno 740 do S23) e Gen8 V37 (a8xx). Ambos vao
 *    para a pasta INTERNA getFilesDir()/gpu_drivers/<slug>/, e o
 *    env.txt recebe DM_VK_DRIVER com o caminho ABSOLUTO interno.
 *    Importar .so ou .zip: mesmo destino, um por vez, para testar.
 *  - Jogar: android.app.NativeActivity neste mesmo pacote. So com
 *    RomFS/ e shaders.dmsv em getExternalFilesDir(); sem dados,
 *    Jogar abre o Importar.
 *  - Configuracoes: SOMENTE as chaves que o host le em settings.txt
 *    (docked, fps, opacity, overlay, rumble). O launcher NUNCA
 *    escreve DM_FSR nem DM_RENDER_SCALE (o host DS nem tem FSR).
 *  - Sobre / log: pacote, versao (marcador desta build), status
 *    dos dados, driver atual e as ultimas 30 linhas do log.
 */
public class DarkSoulsLauncherActivity extends Activity {
    private static final int REQ_PICK_TREE = 2001;
    private static final int REQ_PICK_ZIP = 2002;
    private static final int REQ_PICK_DRIVER_SO = 2003;
    private static final int REQ_PICK_DRIVER_ZIP = 2004;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean importing = false;
    private boolean artLocked = false;
    private AlertDialog importLoadingDialog;
    private TextView importLoadingPct;
    private TextView importLoadingDetail;
    private TextView importLoadingFile;
    private View importLoadingFill;
    private View importLoadingEmpty;
    private int importLastPct = -1;
    private String importCurrentFile = "";
    private Button playBtn;
    private TextView statusText;

    private String driverChoice = "system";
    private String importedSlug = "";
    private String importedSoName = "";
    private Button configDriversBtn;
    // ---- v7: fogo, brasas, YouTube ----
    private MediaPlayer firePlayer;
    private boolean fireMuted = false;
    private EmberView emberView;
    private ObjectAnimator ytPulseX;
    private ObjectAnimator ytPulseY;
    private Button muteBtn;
    private LinearLayout ytPill;
    private Typeface gameFont = null;

    private Typeface gameTypeface() {
        if (gameFont != null) return gameFont;
        // Sem fonte externa: serif negrito do sistema, sem asset para faltar.
        gameFont = Typeface.create(Typeface.SERIF, Typeface.BOLD);
        return gameFont;
    }

    private void applyGameFont(TextView tv, float letterSpacing) {
        try {
            tv.setTypeface(gameTypeface(), Typeface.BOLD);
            tv.setLetterSpacing(letterSpacing);
        } catch (Exception ignored) {
            tv.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        }
    }

    private int setDocked = 1;
    private int setFps = 0;
    private int setOpacity = 60;
    private int setOverlay = 1;
    private int setRumble = 1;

    private static class DocEntry {
        Uri uri;
        String rel;
        long size;
        boolean dir;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setImmersive();
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF050505);
        // Arte da fogueira enviada pelo usuario (Firelink Shrine, chama a direita):
        // tela cheia, CENTER_CROP. A abinha fica na esquerda e o titulo no
        // topo a direita, para nunca cobrir a chama.
        ImageView art = new ImageView(this);
        int artId = getResources().getIdentifier("launcher_art", "drawable", getPackageName());
        if (artId != 0) {
            art.setImageResource(artId);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        }
        root.addView(art, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        View scrim = new View(this);
        scrim.setBackgroundColor(0x22000000);
        root.addView(scrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Brasas subindo + brilho da fogueira tremeluzindo (por cima da arte).
        emberView = new EmberView(this);
        root.addView(emberView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // ---- abinha vertical na esquerda ----
        FrameLayout tabWrap = new FrameLayout(this);
        FrameLayout.LayoutParams tabLp = new FrameLayout.LayoutParams(
                dp(190), FrameLayout.LayoutParams.MATCH_PARENT);
        tabLp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        LinearLayout tab = new LinearLayout(this);
        tab.setOrientation(LinearLayout.VERTICAL);
        tab.setBackgroundColor(0xE6121212);
        tab.setPadding(dp(12), dp(14), dp(12), dp(12));
        TextView tabTitle = new TextView(this);
        tabTitle.setText("DARK SOULS");
        tabTitle.setTextSize(15);
        applyGameFont(tabTitle, 0.08f);
        tabTitle.setTextColor(0xFFC9A227);
        tab.addView(tabTitle);
        View divider = new View(this);
        divider.setBackgroundColor(0xFFC9A227);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(2));
        divLp.topMargin = dp(6);
        divLp.bottomMargin = dp(10);
        tab.addView(divider, divLp);

        playBtn = makeMenuButton("Jogar", true);
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { playGame(); }
        });
        tab.addView(playBtn, menuParams());
        tab.addView(makeMenuButton("Importar jogo", false, new View.OnClickListener() {
            @Override public void onClick(View v) { showImportDialog(); }
        }), menuParams());
        tab.addView(makeMenuButton("Configura\u00e7\u00e3o", false, new View.OnClickListener() {
            @Override public void onClick(View v) { showConfigDialog(); }
        }), menuParams());
        tab.addView(makeMenuButton("Drivers de GPU", false, new View.OnClickListener() {
            @Override public void onClick(View v) { showDriversDialog(); }
        }), menuParams());
        tab.addView(makeMenuButton("Sobre", false, new View.OnClickListener() {
            @Override public void onClick(View v) { showSobreDialog(); }
        }), menuParams());

        statusText = new TextView(this);
        statusText.setTextSize(11);
        statusText.setTextColor(0xFFDDDDDD);
        statusText.setShadowLayer(dp(3), 0, dp(2), 0xFF000000);
        statusText.setPadding(0, dp(10), 0, 0);
        tab.addView(statusText);

        tabWrap.addView(tab, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        View goldEdge = new View(this);
        goldEdge.setBackgroundColor(0xFFC9A227);
        FrameLayout.LayoutParams edgeLp = new FrameLayout.LayoutParams(
                dp(2), FrameLayout.LayoutParams.MATCH_PARENT);
        edgeLp.gravity = Gravity.END;
        tabWrap.addView(goldEdge, edgeLp);
        tabWrap.setAlpha(0f);
        root.addView(tabWrap, tabLp);

        // ---- titulo no topo a direita (sobre a pedra escura, fora da chama) ----
        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.setGravity(Gravity.END);
        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        titleLp.gravity = Gravity.TOP | Gravity.END;
        titleLp.topMargin = dp(52);
        titleLp.rightMargin = dp(16);
        TextView title = new TextView(this);
        title.setText("DARK SOULS");
        title.setTextSize(32);
        applyGameFont(title, 0.08f);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.END);
        title.setShadowLayer(dp(4), 0, dp(2), 0xFF000000);
        titleBlock.addView(title);
        TextView sub = new TextView(this);
        sub.setText("REMASTERED  \u2022  Android Port");
        sub.setTextSize(14);
        applyGameFont(sub, 0.05f);
        sub.setTextColor(0xFFC9A227);
        sub.setGravity(Gravity.END);
        sub.setShadowLayer(dp(3), 0, dp(2), 0xFF000000);
        titleBlock.addView(sub);
        titleBlock.setAlpha(0f);
        root.addView(titleBlock, titleLp);

        // ---- pilula do YouTube: pequena, no topo a direita, com pulso sutil ----
        ytPill = makeYtPill();
        FrameLayout.LayoutParams ytLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        ytLp.gravity = Gravity.TOP | Gravity.END;
        ytLp.topMargin = dp(10);
        ytLp.rightMargin = dp(12);
        root.addView(ytPill, ytLp);

        // ---- mudo do fogo, canto inferior direito ----
        muteBtn = new Button(this);
        muteBtn.setText("Fogo: ON");
        muteBtn.setTextSize(12);
        applyGameFont(muteBtn, 0.04f);
        muteBtn.setTextColor(Color.WHITE);
        GradientDrawable md = new GradientDrawable();
        md.setColor(0xCC141414);
        md.setCornerRadius(dp(12));
        md.setStroke(dp(2), 0xFF8C6A1F);
        muteBtn.setBackground(md);
        muteBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleFireMute(); }
        });
        FrameLayout.LayoutParams muteLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        muteLp.gravity = Gravity.BOTTOM | Gravity.END;
        muteLp.bottomMargin = dp(10);
        muteLp.rightMargin = dp(12);
        root.addView(muteBtn, muteLp);

        setContentView(root);
        // Fade-in do titulo e da abinha ao abrir.
        titleBlock.animate().alpha(1f).setDuration(900).start();
        tabWrap.animate().alpha(1f).setDuration(900).setStartDelay(150).start();
        // Pulso sutil da pilula do canal (chama o olho sem ser gigante).
        ytPulseX = ObjectAnimator.ofFloat(ytPill, "scaleX", 1f, 1.06f, 1f);
        ytPulseX.setDuration(1600);
        ytPulseX.setRepeatCount(ValueAnimator.INFINITE);
        ytPulseX.start();
        ytPulseY = ObjectAnimator.ofFloat(ytPill, "scaleY", 1f, 1.06f, 1f);
        ytPulseY.setDuration(1600);
        ytPulseY.setRepeatCount(ValueAnimator.INFINITE);
        ytPulseY.start();
        startFireLoop();
        loadSettings();
        loadDriverChoice();
        refreshPlay();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (emberView != null) emberView.setRunning(true);
        resumeFireLoop();
        loadSettings();
        loadDriverChoice();
        refreshPlay();
    }

    @Override
    protected void onPause() {
        if (emberView != null) emberView.setRunning(false);
        pauseFireLoop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (ytPulseX != null) ytPulseX.cancel();
        if (ytPulseY != null) ytPulseY.cancel();
        stopFireLoop();
        super.onDestroy();
    }

    // ---- fogo: loop de crepitacao (res/raw/fire_loop.mp3) ----
    private void startFireLoop() {
        try {
            if (firePlayer != null) return;
            int id = getResources().getIdentifier("fire_loop", "raw", getPackageName());
            if (id == 0) return;
            firePlayer = MediaPlayer.create(this, id);
            if (firePlayer == null) return;
            firePlayer.setLooping(true);
            firePlayer.setVolume(fireMuted ? 0f : 0.35f, fireMuted ? 0f : 0.35f);
            firePlayer.start();
        } catch (Exception ignored) {
            firePlayer = null; // o launcher nunca trava por causa do som
        }
    }

    private void pauseFireLoop() {
        try {
            if (firePlayer != null && firePlayer.isPlaying()) firePlayer.pause();
        } catch (Exception ignored) {}
    }

    private void resumeFireLoop() {
        try {
            if (firePlayer != null && !fireMuted && !firePlayer.isPlaying()) firePlayer.start();
        } catch (Exception ignored) {}
    }

    private void stopFireLoop() {
        try {
            if (firePlayer != null) { firePlayer.stop(); firePlayer.release(); }
        } catch (Exception ignored) {
        } finally {
            firePlayer = null;
        }
    }

    private void toggleFireMute() {
        fireMuted = !fireMuted;
        try {
            if (firePlayer != null) {
                firePlayer.setVolume(fireMuted ? 0f : 0.35f, fireMuted ? 0f : 0.35f);
                if (!fireMuted && !firePlayer.isPlaying()) firePlayer.start();
            }
        } catch (Exception ignored) {}
        if (muteBtn != null) muteBtn.setText(fireMuted ? "Fogo: OFF" : "Fogo: ON");
    }

    // ---- canal do YouTube ----
    private void openYouTube() {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://youtube.com/@emuucheck"));
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Nao foi possivel abrir o YouTube.", Toast.LENGTH_SHORT).show();
        }
    }

    private LinearLayout makeYtPill() {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xFFE62117);
        d.setCornerRadius(dp(16));
        d.setStroke(dp(2), 0xFFFFFFFF);
        pill.setBackground(d);
        pill.setPadding(dp(8), dp(4), dp(10), dp(4));
        ImageView play = new ImageView(this);
        int playId = getResources().getIdentifier("ic_yt_play", "drawable", getPackageName());
        if (playId != 0) play.setImageResource(playId);
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(dp(18), dp(18));
        playLp.rightMargin = dp(5);
        pill.addView(play, playLp);
        TextView label = new TextView(this);
        label.setText("@emuucheck");
        label.setTextSize(12);
        applyGameFont(label, 0.02f);
        label.setTextColor(Color.WHITE);
        pill.addView(label);
        pill.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openYouTube(); }
        });
        return pill;
    }

    // Brasas subindo da fogueira + brilho tremeluzindo. ~30 fps, so roda
    // com a activity visivel (setRunning em onResume/onPause).
    private static class EmberView extends View {
        private static class Ember {
            float x, y, speed, sway, phase, size, alpha;
            int color;
        }
        private final java.util.List<Ember> embers = new java.util.ArrayList<Ember>();
        private final Paint paint = new Paint();
        private final Paint glowPaint = new Paint();
        private final java.util.Random rnd = new java.util.Random(20261109);
        private final int[] emberColors = {0xFFFF8C1A, 0xFFFFB347, 0xFFFF5A00, 0xFFFFD27A};
        private final float density;
        private boolean running = false;
        private long startMs = System.currentTimeMillis();
        private RadialGradient glowGrad;
        private float glowCx, glowCy, glowR;
        private final Runnable tick = new Runnable() {
            @Override public void run() {
                if (!running) return;
                invalidate();
                postDelayed(this, 33);
            }
        };

        EmberView(Context ctx) {
            super(ctx);
            density = ctx.getResources().getDisplayMetrics().density;
            paint.setAntiAlias(true);
            glowPaint.setAntiAlias(true);
        }

        void setRunning(boolean r) {
            if (r == running) return;
            running = r;
            if (r) {
                startMs = System.currentTimeMillis();
                post(tick);
            } else {
                removeCallbacks(tick);
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w <= 0 || h <= 0) return;
            glowCx = 0.64f * w;
            glowCy = 0.42f * h;
            glowR = 0.38f * h;
            glowGrad = new RadialGradient(glowCx, glowCy, glowR,
                    0x66FF7A1A, 0x00FF7A1A, Shader.TileMode.CLAMP);
            glowPaint.setShader(glowGrad);
            if (embers.isEmpty()) {
                for (int i = 0; i < 24; i++) {
                    Ember e = new Ember();
                    resetEmber(e, w, h, true);
                    embers.add(e);
                }
            }
        }

        private void resetEmber(Ember e, int w, int h, boolean anywhere) {
            e.x = (0.55f + rnd.nextFloat() * 0.28f) * w;
            e.y = anywhere ? rnd.nextFloat() * h : h * (0.92f + rnd.nextFloat() * 0.08f);
            e.speed = 0.03f + rnd.nextFloat() * 0.05f; // fracao da altura por segundo
            e.sway = 0.4f + rnd.nextFloat() * 1.2f;
            e.phase = rnd.nextFloat() * 6.28f;
            e.size = (2f + rnd.nextFloat() * 3f) * density;
            e.alpha = 0.4f + rnd.nextFloat() * 0.55f;
            e.color = emberColors[rnd.nextInt(emberColors.length)];
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0 || glowGrad == null) return;
            float t = (System.currentTimeMillis() - startMs) / 1000f;
            float flick = 0.55f + 0.25f * (float) Math.sin(t * 2.1f)
                    + 0.2f * (float) Math.sin(t * 5.3f + 1.7f);
            if (flick < 0.15f) flick = 0.15f;
            if (flick > 1f) flick = 1f;
            glowPaint.setAlpha((int) (flick * 160));
            canvas.drawCircle(glowCx, glowCy, glowR, glowPaint);
            for (Ember e : embers) {
                e.y -= e.speed * h * 0.033f;
                if (e.y < -10) resetEmber(e, w, h, false);
                float x = e.x + (float) Math.sin(t * 1.3f + e.phase) * e.sway * 8f;
                float a = e.alpha * (0.7f + 0.3f * (float) Math.sin(t * 7f + e.phase * 3f));
                if (a < 0.05f) a = 0.05f;
                if (a > 1f) a = 1f;
                paint.setColor(e.color);
                paint.setAlpha((int) (a * 255));
                canvas.drawCircle(x, e.y, e.size, paint);
            }
        }
    }

    private LinearLayout.LayoutParams menuParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(3);
        lp.bottomMargin = dp(3);
        return lp;
    }

    // Linha de 2 botoes lado a lado, mesma tonalidade escura da foto.
    private LinearLayout buttonRow(Button a, Button b) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = dp(4);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        rp.leftMargin = dp(4);
        row.addView(a, lp);
        row.addView(b, rp);
        return row;
    }

    private Button makeMenuButton(String text, boolean primary) {
        return makeMenuButton(text, primary, null);
    }

    private Button makeMenuButton(String text, boolean primary, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(14);
        applyGameFont(b, 0.04f);
        b.setTextColor(Color.WHITE);
        b.setMinimumHeight(dp(44));
        b.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable d = new GradientDrawable();
        // Transluzido, na tonalidade da foto: a foto continua visivel
        // atras dos botoes; nada de botao branco/opaco escondendo a foto.
        d.setColor(primary ? 0xDD6B4E1A : 0xCC141414);
        d.setCornerRadius(dp(12));
        d.setStroke(dp(2), 0xFF8C6A1F);
        b.setBackground(d);
        if (l != null) b.setOnClickListener(l);
        return b;
    }

    private void refreshPlay() {
        boolean ok = hasRomFs() && hasShaders();
        if (playBtn != null) {
            playBtn.setEnabled(true);
            playBtn.setAlpha(ok ? 1f : 0.55f);
            playBtn.setText(ok ? "Jogar" : "Jogar (sem dados)");
        }
        if (statusText != null) {
            statusText.setText(ok ? "Dados prontos: " + shaderText()
                    : "Sem dados: toque em Jogar ou em Selecionar pasta para importar.");
        }
    }

    private void playGame() {
        if (artLocked) return;
        if (hasRomFs() && hasShaders()) {
            // Antes de Jogar, garantir o estado do driver: se Turnip
            // esta escolhido mas o .so sumiu, tentar extrair de novo;
            // se ainda faltar, voltar sozinho ao Sistema (nunca travar).
            // E SEMPRE remover DM_FSR / DM_RENDER_SCALE do env.txt, para
            // o host com FSR nunca ligar FSR/escala por acidente.
            File so = driverSoFile();
            if (!"system".equals(driverChoice) && (so == null || !so.isFile())) {
                boolean recovered = driverChoice.startsWith("turnip-") && extractBundledTurnip(driverChoice, driverChoice + ".zip");
                if (!recovered) {
                    driverChoice = "system";
                    writeEnvDriver();
                    Toast.makeText(this, "Driver indisponivel: voltando ao driver do Sistema.", Toast.LENGTH_SHORT).show();
                }
            }
            pauseFireLoop();
            sanitizeEnv();
            Intent i = new Intent();
            i.setClassName(getPackageName(), "android.app.NativeActivity");
            startActivity(i);
        } else {
            showImportDialog();
        }
    }

    private void setImmersive() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) setImmersive();
    }

    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return (int) (v * d + 0.5f);
    }

    private GradientDrawable dialogBg() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF141414);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(2), 0xFF8C6A1F);
        return bg;
    }

    private void styleDialogButton(Button b) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xFF6B4E1A);
        d.setCornerRadius(dp(12));
        d.setStroke(dp(2), 0xFF8C6A1F);
        b.setBackground(d);
        b.setTextColor(Color.WHITE);
        applyGameFont(b, 0.04f);
        b.setTextSize(15);
        b.setMinimumHeight(dp(44));
        b.setPadding(dp(10), dp(6), dp(10), dp(6));
    }

    private LinearLayout.LayoutParams spacedParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        lp.bottomMargin = dp(6);
        return lp;
    }

    private AlertDialog themed(AlertDialog.Builder b) {
        AlertDialog d = b.create();
        d.show();
        android.view.Window w = d.getWindow();
        if (w != null) w.setBackgroundDrawable(dialogBg());
        Button pos = d.getButton(AlertDialog.BUTTON_POSITIVE);
        if (pos != null) styleDialogButton(pos);
        Button neg = d.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (neg != null) styleDialogButton(neg);
        return d;
    }

    private File dataDir() {
        File ext = getExternalFilesDir(null);
        return ext != null ? ext : getFilesDir();
    }

    private boolean hasRomFs() {
        return new File(dataDir(), "RomFS").isDirectory();
    }

    private boolean hasShaders() {
        return new File(dataDir(), "shaders.dmsv").isFile();
    }

    private boolean hasEnv() {
        return new File(dataDir(), "env.txt").isFile();
    }

    private String shaderText() {
        File f = new File(dataDir(), "shaders.dmsv");
        if (!f.isFile()) return "ausente";
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            byte[] h = new byte[12];
            if (in.read(h) != 12) return "ileg\u00edvel";
            if (h[0] != 'D' || h[1] != 'M' || h[2] != 'S' || h[3] != 'V') return "formato desconhecido";
            int count = (h[8] & 0xFF) | ((h[9] & 0xFF) << 8)
                    | ((h[10] & 0xFF) << 16) | ((h[11] & 0xFF) << 24);
            return count + " shaders (" + (f.length() / 1024 / 1024) + " MB)";
        } catch (Exception e) {
            return "ileg\u00edvel";
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
        }
    }

    private void loadSettings() {
        setDocked = 0; setFps = 1; setOpacity = 60; setOverlay = 1; setRumble = 1;
        File f = new File(dataDir(), "settings.txt");
        if (!f.isFile()) return;
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(f));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("docked=")) setDocked = parseInt(line.substring(7), setDocked);
                else if (line.startsWith("fps=")) setFps = parseInt(line.substring(4), setFps);
                else if (line.startsWith("opacity=")) setOpacity = parseInt(line.substring(8), setOpacity);
                else if (line.startsWith("overlay=")) setOverlay = parseInt(line.substring(8), setOverlay);
                else if (line.startsWith("rumble=")) setRumble = parseInt(line.substring(7), setRumble);
            }
        } catch (Exception ignored) {
        } finally {
            try { if (r != null) r.close(); } catch (Exception ignored) {}
        }
        if (setOpacity < 20) setOpacity = 20;
        if (setOpacity > 80) setOpacity = 80;
    }

    private int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; }
    }


    // ---- Drivers de GPU (Turnip via adrenotools) ----
    // Driver vive em getFilesDir()/gpu_drivers/<slug>/, NUNCA no
    // armazenamento externo: o adrenotools recusa driver externo
    // (foi o bug da v3 antiga). O env.txt do jogo recebe SOMENTE
    // DM_VK_DRIVER com o caminho ABSOLUTO interno. Sem Turnip
    // escolhido, a chave nao existe e o host usa o driver do Sistema
    // (comportamento identico a v7). O host com adrenotools, se o
    // Turnip falhar, volta sozinho ao Sistema e loga o fallback.
    private File driversDir() {
        return new File(getFilesDir(), "gpu_drivers");
    }

    private File turnipDir(String slug) {
        return new File(driversDir(), slug);
    }

    private File turnipSo(String slug) {
        return new File(turnipDir(slug), "libvulkan_freedreno.so");
    }

    private String driverLabel() {
        if ("turnip-a7xx".equals(driverChoice)) return "Turnip v26.3.0-R6 (a7xx)";
        if ("turnip-a8xx".equals(driverChoice)) return "Turnip Gen8 V37 (a8xx)";
        if ("imported".equals(driverChoice) && !importedSlug.isEmpty()) return "Importado: " + importedSlug;
        return "Sistema";
    }

    private File driverSoFile() {
        if ("turnip-a7xx".equals(driverChoice) || "turnip-a8xx".equals(driverChoice)) return turnipSo(driverChoice);
        if ("imported".equals(driverChoice) && !importedSlug.isEmpty() && !importedSoName.isEmpty())
            return new File(new File(driversDir(), importedSlug), importedSoName);
        return null;
    }

    private java.util.List<String[]> listImportedDrivers() {
        java.util.List<String[]> out = new ArrayList<String[]>();
        File root = driversDir();
        File[] dirs = root.isDirectory() ? root.listFiles() : null;
        if (dirs == null) return out;
        java.util.Arrays.sort(dirs);
        for (File d : dirs) {
            if (!d.isDirectory() || "turnip-a7xx".equals(d.getName()) || "turnip-a8xx".equals(d.getName())) continue;
            File[] files = d.listFiles();
            if (files == null) continue;
            for (File f : files) {
                if (f.isFile() && f.getName().endsWith(".so")) {
                    out.add(new String[]{d.getName(), f.getName()});
                    break;
                }
            }
        }
        return out;
    }

    private void loadDriverChoice() {
        driverChoice = "system";
        File env = new File(dataDir(), "env.txt");
        if (!env.isFile()) return;
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(env));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("DM_VK_DRIVER=")) {
                    String v = line.substring("DM_VK_DRIVER=".length()).trim();
                    if (v.contains("/turnip-a7xx/")) {
                        driverChoice = "turnip-a7xx";
                    } else if (v.contains("/turnip-a8xx/")) {
                        driverChoice = "turnip-a8xx";
                    } else if (v.contains("/gpu_drivers/")) {
                        int idx = v.indexOf("/gpu_drivers/") + "/gpu_drivers/".length();
                        String rest = v.substring(idx);
                        int slash = rest.indexOf('/');
                        if (slash > 0) {
                            driverChoice = "imported";
                            importedSlug = rest.substring(0, slash);
                            importedSoName = rest.substring(slash + 1);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            try { if (r != null) r.close(); } catch (Exception ignored) {}
        }
    }

    // Escreve (ou remove) DM_VK_DRIVER no env.txt, preservando as
    // outras linhas. NUNCA escreve DM_FSR, DM_RENDER_SCALE ou
    // DM_VK_HOOK_DIR.
    private void writeEnvDriver() {
        File env = new File(dataDir(), "env.txt");
        List<String> lines = new ArrayList<String>();
        if (env.isFile()) {
            BufferedReader r = null;
            try {
                r = new BufferedReader(new FileReader(env));
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("DM_VK_DRIVER=")) continue;
                    lines.add(line);
                }
            } catch (Exception ignored) {
            } finally {
                try { if (r != null) r.close(); } catch (Exception ignored) {}
            }
        }
        File so = driverSoFile();
        if (!"system".equals(driverChoice) && so != null && so.isFile()) {
            lines.add("DM_VK_DRIVER=" + so.getAbsolutePath());
        }
        File tmp = new File(dataDir(), "env.txt.tmp");
        try {
            FileOutputStream out = new FileOutputStream(tmp);
            for (String l : lines) out.write((l + "\n").getBytes("UTF-8"));
            out.close();
            if (env.exists() && !env.delete()) throw new java.io.IOException("nao foi possivel substituir env.txt");
            if (!tmp.renameTo(env)) throw new java.io.IOException("falha ao finalizar env.txt");
        } catch (Exception e) {
            Toast.makeText(this, "Falha ao salvar driver: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Remove DM_FSR / DM_RENDER_SCALE / DM_VK_HOOK_DIR do env.txt,
    // preservando o resto (inclusive DM_VK_DRIVER). Chamado antes de
    // todo Jogar: FSR e escala ficam desligados mesmo se alguem
    // editou o env.txt a mao.
    private void sanitizeEnv() {
        File env = new File(dataDir(), "env.txt");
        if (!env.isFile()) { writeEnvDriver(); return; }
        List<String> lines = new ArrayList<String>();
        boolean changed = false;
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(env));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("DM_FSR=") || line.startsWith("DM_RENDER_SCALE=")
                        || line.startsWith("DM_VK_HOOK_DIR=")) { changed = true; continue; }
                lines.add(line);
            }
        } catch (Exception ignored) {
            return;
        } finally {
            try { if (r != null) r.close(); } catch (Exception ignored) {}
        }
        if (!changed) return;
        File tmp = new File(dataDir(), "env.txt.tmp");
        try {
            FileOutputStream out = new FileOutputStream(tmp);
            for (String l : lines) out.write((l + "\n").getBytes("UTF-8"));
            out.close();
            if (env.exists() && !env.delete()) return;
            tmp.renameTo(env);
        } catch (Exception ignored) {}
    }

    // Extrai um Turnip incluso do asset para a pasta interna. Retorna true
    // se o .so ficou presente e legivel. slug e asset tem o mesmo nome
    // base: turnip-a7xx.zip / turnip-a8xx.zip.
    private boolean extractBundledTurnip(String slug, String asset) {
        try {
            File dir = turnipDir(slug);
            dir.mkdirs();
            InputStream in = getAssets().open(asset);
            java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(in);
            java.util.zip.ZipEntry ze;
            while ((ze = zin.getNextEntry()) != null) {
                String name = ze.getName();
                if (name.contains("..") || name.startsWith("/")) continue;
                File dest = new File(dir, name);
                if (ze.isDirectory() || name.endsWith("/")) { dest.mkdirs(); continue; }
                dest.getParentFile().mkdirs();
                File tmp = new File(dest.getParentFile(), dest.getName() + ".tmp");
                FileOutputStream out = new FileOutputStream(tmp);
                byte[] buf = new byte[262144];
                int n;
                while ((n = zin.read(buf)) >= 0) out.write(buf, 0, n);
                out.close();
                zin.closeEntry();
                if (dest.exists() && !dest.delete()) return false;
                if (!tmp.renameTo(dest)) return false;
            }
            zin.close();
            return turnipSo(slug).isFile() && turnipSo(slug).length() > 1000000;
        } catch (Exception e) {
            return false;
        }
    }

    private void showDriversDialog() {
        loadDriverChoice();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(8), dp(16), dp(8));
        TextView cur = new TextView(this);
        cur.setTextSize(14);
        cur.setTextColor(Color.WHITE);
        cur.setText("Driver atual: " + driverLabel());
        col.addView(cur, spacedParams());
        // Importacao NO TOPO (v10): impossivel de nao achar, sem limite
        // de drivers mostrados. Scroll liberado so aqui e em Configuracao.
        Button impSo = new Button(this);
        impSo.setText("Importar driver (.so)");
        impSo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickDriverSo(); }
        });
        styleDialogButton(impSo); col.addView(impSo, spacedParams());
        Button impZip = new Button(this);
        impZip.setText("Importar driver (.zip)");
        impZip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickDriverZip(); }
        });
        styleDialogButton(impZip); col.addView(impZip, spacedParams());
        Button sys = new Button(this);
        sys.setText("Sistema (driver do celular)");
        sys.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                driverChoice = "system";
                writeEnvDriver();
                Toast.makeText(DarkSoulsLauncherActivity.this, "Driver: Sistema", Toast.LENGTH_SHORT).show();
                if (configDriversBtn != null) configDriversBtn.setText("Drivers de GPU (atual: Sistema)");
                refreshPlay();
            }
        });
        styleDialogButton(sys); col.addView(sys, spacedParams());
        Button turnipA7 = new Button(this);
        turnipA7.setText("Turnip v26.3.0-R6 (a7xx/a6xx, Mesa) - certo para o S23");
        turnipA7.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!turnipSo("turnip-a7xx").isFile() && !extractBundledTurnip("turnip-a7xx", "turnip-a7xx.zip")) {
                    Toast.makeText(DarkSoulsLauncherActivity.this, "Falha ao extrair o Turnip do APK.", Toast.LENGTH_SHORT).show();
                    return;
                }
                driverChoice = "turnip-a7xx";
                writeEnvDriver();
                Toast.makeText(DarkSoulsLauncherActivity.this, "Driver: Turnip v26.3.0-R6. Toque em Jogar e mande o log.txt.", Toast.LENGTH_SHORT).show();
                if (configDriversBtn != null) configDriversBtn.setText("Drivers de GPU (atual: Turnip v26.3.0-R6 (a7xx))");
                refreshPlay();
            }
        });
        styleDialogButton(turnipA7); col.addView(turnipA7, spacedParams());
        Button turnipA8 = new Button(this);
        turnipA8.setText("Turnip Gen8 V37 (a8xx, Mesa) - nao e o do S23");
        turnipA8.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!turnipSo("turnip-a8xx").isFile() && !extractBundledTurnip("turnip-a8xx", "turnip-a8xx.zip")) {
                    Toast.makeText(DarkSoulsLauncherActivity.this, "Falha ao extrair o Turnip do APK.", Toast.LENGTH_SHORT).show();
                    return;
                }
                driverChoice = "turnip-a8xx";
                writeEnvDriver();
                Toast.makeText(DarkSoulsLauncherActivity.this, "Driver: Turnip Gen8 V37. Toque em Jogar e mande o log.txt.", Toast.LENGTH_SHORT).show();
                if (configDriversBtn != null) configDriversBtn.setText("Drivers de GPU (atual: Turnip Gen8 V37 (a8xx))");
                refreshPlay();
            }
        });
        styleDialogButton(turnipA8); col.addView(turnipA8, spacedParams());
        java.util.List<String[]> imported = listImportedDrivers();
        for (final String[] d : imported) {
            Button use = new Button(this);
            use.setText("Usar importado: " + d[0]);
            use.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    driverChoice = "imported";
                    importedSlug = d[0];
                    importedSoName = d[1];
                    writeEnvDriver();
                    Toast.makeText(DarkSoulsLauncherActivity.this, "Driver: " + d[0], Toast.LENGTH_SHORT).show();
                    if (configDriversBtn != null) configDriversBtn.setText("Drivers de GPU (atual: Importado: " + d[0] + ")");
                    refreshPlay();
                }
            });
            styleDialogButton(use); col.addView(use, spacedParams());
        }
        TextView note = new TextView(this);
        note.setTextSize(12);
        note.setTextColor(Color.WHITE);
        note.setText("Importado vai para gpu_drivers/<nome>/ na pasta interna. Se nao carregar, o host volta ao Sistema. Aceita qualquer .so; nome fora do padrao libvulkan_*.so gera aviso, mas importa mesmo assim.");
        col.addView(note);
        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        themed(new AlertDialog.Builder(this).setTitle("Drivers de GPU").setView(sv)
                .setPositiveButton("Fechar", null));
    }

    private void pickDriverSo() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_PICK_DRIVER_SO);
    }

    private void pickDriverZip() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        startActivityForResult(i, REQ_PICK_DRIVER_ZIP);
    }

    private String driverFileName(Uri uri) {
        String name = null;
        android.database.Cursor c = null;
        try {
            c = getContentResolver().query(uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) name = c.getString(0);
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        if (name == null || name.isEmpty()) {
            String path = uri.getLastPathSegment();
            name = path != null ? path : "driver.so";
        }
        return name;
    }

    private boolean looksLikeVulkanDriver(String name) {
        return name != null && name.endsWith(".so") && name.contains("vulkan");
    }

    private void importDriverSo(Uri uri) {
        String name = driverFileName(uri);
        if (name.contains("..") || name.contains("/")) {
            Toast.makeText(this, "Nome de arquivo invalido.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!name.endsWith(".so")) {
            Toast.makeText(this, "Falha ao importar driver: o arquivo nao e .so (" + name + "). Escolha um arquivo .so ou um zip com .so dentro.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!name.contains("vulkan")) {
            Toast.makeText(this, "Aviso: nome fora do padrao libvulkan_*.so, importado mesmo assim.", Toast.LENGTH_LONG).show();
        }
        String slug = name.replace(".so", "");
        if (slug.startsWith("lib")) slug = slug.substring(3);
        if (slug.isEmpty()) slug = "importado";
        try {
            File dir = new File(driversDir(), slug);
            dir.mkdirs();
            File dest = new File(dir, name);
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) throw new java.io.IOException("nao foi possivel abrir o arquivo");
            File tmp = new File(dir, name + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            byte[] buf = new byte[262144];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            in.close();
            out.close();
            if (dest.exists() && !dest.delete()) throw new java.io.IOException("nao foi possivel substituir o driver");
            if (!tmp.renameTo(dest)) throw new java.io.IOException("falha ao finalizar o driver");
            driverChoice = "imported";
            importedSlug = slug;
            importedSoName = name;
            writeEnvDriver();
            Toast.makeText(this, "Driver importado: " + slug + ". Toque em Jogar.", Toast.LENGTH_SHORT).show();
            if (configDriversBtn != null) configDriversBtn.setText("Drivers de GPU (atual: Importado: " + slug + ")");
            refreshPlay();
        } catch (Exception e) {
            Toast.makeText(this, "Falha ao importar driver: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void importDriverZip(Uri uri) {
        String zipName = driverFileName(uri);
        String slug = zipName.replace(".zip", "").replace(".ZIP", "");
        if (slug.isEmpty() || slug.contains("..") || slug.contains("/")) slug = "importado";
        try {
            InputStream raw = getContentResolver().openInputStream(uri);
            if (raw == null) throw new java.io.IOException("nao foi possivel abrir o zip");
            ZipInputStream zin = new ZipInputStream(raw);
            java.util.List<String> soNames = new java.util.ArrayList<String>();
            java.util.List<byte[]> soBytes = new java.util.ArrayList<byte[]>();
            ZipEntry ze;
            while ((ze = zin.getNextEntry()) != null) {
                String name = ze.getName();
                if (!ze.isDirectory() && name.endsWith(".so") && !name.contains("..")) {
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[262144];
                    int n;
                    while ((n = zin.read(buf)) >= 0) baos.write(buf, 0, n);
                    int slash = name.lastIndexOf('/');
                    soNames.add(slash >= 0 ? name.substring(slash + 1) : name);
                    soBytes.add(baos.toByteArray());
                }
                zin.closeEntry();
            }
            zin.close();
            if (soNames.isEmpty()) {
                Toast.makeText(this, "Falha ao importar driver: zip sem nenhum arquivo .so dentro.", Toast.LENGTH_LONG).show();
                return;
            }
            int chosen = 0;
            for (int i = 0; i < soNames.size(); i++) {
                if (soNames.get(i).contains("vulkan")) { chosen = i; break; }
            }
            String foundName = soNames.get(chosen);
            byte[] found = soBytes.get(chosen);
            if (!foundName.contains("vulkan")) {
                Toast.makeText(this, "Aviso: nenhum libvulkan_*.so no zip; importado o primeiro .so (" + foundName + ") mesmo assim.", Toast.LENGTH_LONG).show();
            }
            File dir = new File(driversDir(), slug);
            dir.mkdirs();
            File dest = new File(dir, foundName);
            File tmp = new File(dir, foundName + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            out.write(found);
            out.close();
            if (dest.exists() && !dest.delete()) throw new java.io.IOException("nao foi possivel substituir o driver");
            if (!tmp.renameTo(dest)) throw new java.io.IOException("falha ao finalizar o driver");
            driverChoice = "imported";
            importedSlug = slug;
            importedSoName = foundName;
            writeEnvDriver();
            Toast.makeText(this, "Driver importado: " + slug + ". Toque em Jogar.", Toast.LENGTH_SHORT).show();
            if (configDriversBtn != null) configDriversBtn.setText("Drivers de GPU (atual: Importado: " + slug + ")");
            refreshPlay();
        } catch (Exception e) {
            Toast.makeText(this, "Falha ao importar driver: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void saveSettings() {
        File dest = new File(dataDir(), "settings.txt");
        List<String> lines = new ArrayList<String>();
        if (dest.isFile()) {
            BufferedReader r = null;
            try {
                r = new BufferedReader(new FileReader(dest));
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("docked=") || line.startsWith("fps=")
                            || line.startsWith("opacity=") || line.startsWith("overlay=")
                            || line.startsWith("rumble=")) continue;
                    lines.add(line);
                }
            } catch (Exception ignored) {
            } finally {
                try { if (r != null) r.close(); } catch (Exception ignored) {}
            }
        }
        lines.add(0, "opacity=" + setOpacity);
        lines.add(0, "fps=" + setFps);
        lines.add(0, "docked=" + setDocked);
        lines.add(0, "rumble=" + setRumble);
        lines.add(0, "overlay=" + setOverlay);
        File tmp = new File(dataDir(), "settings.txt.tmp");
        try {
            FileOutputStream out = new FileOutputStream(tmp);
            for (String l : lines) out.write((l + "\n").getBytes("UTF-8"));
            out.close();
            if (dest.exists() && !dest.delete()) throw new java.io.IOException("nao foi possivel substituir settings.txt");
            if (!tmp.renameTo(dest)) throw new java.io.IOException("falha ao finalizar settings.txt");
        } catch (Exception e) {
            postStatus("Falha ao salvar configuracoes: " + e.getMessage());
        }
        refreshPlay();
    }

    private void showConfigDialog() {
        loadSettings();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(8), dp(16), dp(8));
        final Button mode = new Button(this);
        final Button fps = new Button(this);
        final Button overlay = new Button(this);
        final Button rumble = new Button(this);
        final TextView opText = new TextView(this);
        opText.setTextSize(14);
        opText.setTextColor(Color.WHITE);
        final Runnable refresh = new Runnable() {
            @Override public void run() {
                mode.setText("Modo: " + (setDocked == 0 ? "Handheld" : "Docked"));
                fps.setText("Mostrar FPS: " + (setFps == 0 ? "OFF" : "ON"));
                overlay.setText("Gamepad na tela: " + (setOverlay == 0 ? "OFF" : "ON"));
                rumble.setText("Vibra\u00e7\u00e3o: " + (setRumble == 0 ? "OFF" : "ON"));
                opText.setText("Opacidade do gamepad: " + setOpacity + "% (20-80%)");
            }
        };
        mode.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setDocked = setDocked == 0 ? 1 : 0; saveSettings(); refresh.run(); }
        });
        fps.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setFps = setFps == 0 ? 1 : 0; saveSettings(); refresh.run(); }
        });
        overlay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setOverlay = setOverlay == 0 ? 1 : 0; saveSettings(); refresh.run(); }
        });
        rumble.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setRumble = setRumble == 0 ? 1 : 0; saveSettings(); refresh.run(); }
        });
        configDriversBtn = new Button(this);
        configDriversBtn.setText("Drivers de GPU (atual: " + driverLabel() + ")");
        configDriversBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showDriversDialog(); }
        });
        styleDialogButton(configDriversBtn); col.addView(configDriversBtn, spacedParams());
        styleDialogButton(mode); col.addView(mode, spacedParams());
        styleDialogButton(fps); col.addView(fps, spacedParams());
        styleDialogButton(overlay); col.addView(overlay, spacedParams());
        styleDialogButton(rumble); col.addView(rumble, spacedParams());
        col.addView(opText);
        LinearLayout opRow = new LinearLayout(this);
        opRow.setOrientation(LinearLayout.HORIZONTAL);
        Button down = new Button(this);
        down.setText("Opacidade -");
        down.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setOpacity = Math.max(20, setOpacity - 5); saveSettings(); refresh.run(); }
        });
        Button up = new Button(this);
        up.setText("Opacidade +");
        up.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setOpacity = Math.min(80, setOpacity + 5); saveSettings(); refresh.run(); }
        });
        styleDialogButton(down); styleDialogButton(up);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        dl.rightMargin = dp(4);
        LinearLayout.LayoutParams ul = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        ul.leftMargin = dp(4);
        opRow.addView(down, dl); opRow.addView(up, ul);
        col.addView(opRow);
        Button controlesBtn = new Button(this);
        controlesBtn.setText("Controles");
        controlesBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showControlesDialog(); }
        });
        styleDialogButton(controlesBtn); col.addView(controlesBtn, spacedParams());
        Button sobreBtn = new Button(this);
        sobreBtn.setText("Sobre");
        sobreBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSobreDialog(); }
        });
        styleDialogButton(sobreBtn); col.addView(sobreBtn, spacedParams());
        TextView note = new TextView(this);
        note.setTextSize(13);
        note.setTextColor(Color.WHITE);
        note.setText("Controles, Sobre e log ficam aqui, dentro da Configuracao. Drivers: Drivers de GPU (Sistema, Turnip a7xx, Turnip a8xx ou importado). So DM_VK_DRIVER e escrito, nunca DM_FSR/DM_RENDER_SCALE.");
        col.addView(note);
        refresh.run();
        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        themed(new AlertDialog.Builder(this).setTitle("Configura\u00e7\u00e3o").setView(sv)
                .setPositiveButton("Fechar", null));
    }

    private void showControlesDialog() {
        String text = "Editar layout no jogo: pausa > Gamepad > Editar layout.\n"
                + "Arrastar move o botao; pinca ou - / + muda o tamanho (0,5x a 2x).\n"
                + "Ocultar esconde o botao escolhido (o botao de pausa nunca some);\n"
                + "para mostrar de novo, abra o editor: ele aparece apagado.\n"
                + "Salvar trava o layout; Resetar volta tudo ao padrao.\n"
                + "O launcher nao edita o layout direto.";
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(8), dp(16), dp(8));
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(Color.WHITE);
        col.addView(tv);
        Button play = new Button(this);
        play.setText("Editar controles no jogo");
        play.setEnabled(hasRomFs() && hasShaders());
        play.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { playGame(); }
        });
        styleDialogButton(play); col.addView(play, spacedParams());
        themed(new AlertDialog.Builder(this).setTitle("Controles").setView(col)
                .setPositiveButton("Fechar", null));
    }

    private void showSobreDialog() {
        String ver = "desconhecida";
        int code = -1;
        try {
            android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            ver = pi.versionName;
            code = pi.versionCode;
        } catch (Exception ignored) {}
        // v11: o log fica DENTRO do Sobre, e o launcher CRIA o
        // log.txt se ele nao existir, em vez de so dizer "ausente".
        File log = new File(dataDir(), "log.txt");
        String logTail;
        try {
            dataDir().mkdirs();
            if (!log.isFile()) {
                new FileOutputStream(log, true).close();
                logTail = "(log criado agora, vazio - rode o jogo uma vez para gerar o log.)";
            } else {
                java.util.List<String> lines = new ArrayList<String>();
                BufferedReader r = new BufferedReader(new FileReader(log));
                String line;
                while ((line = r.readLine()) != null) lines.add(line);
                r.close();
                if (lines.isEmpty()) {
                    logTail = "(log vazio - rode o jogo uma vez para gerar o log.)";
                } else {
                    int start = Math.max(0, lines.size() - 30);
                    StringBuilder sb = new StringBuilder();
                    for (int i = start; i < lines.size(); i++) sb.append(lines.get(i)).append("\n");
                    logTail = sb.toString();
                }
            }
        } catch (Exception e) {
            logTail = "Nao foi possivel ler o log: " + e.getMessage();
        }
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(8), dp(16), dp(8));
        TextView info = new TextView(this);
        info.setTextSize(13);
        info.setTextColor(Color.WHITE);
        info.setText("DARK SOULS: REMASTERED - Android Port\n"
                + "Versao: " + ver + " (versionCode " + code + ")\n"
                + "RomFS: " + (hasRomFs() ? "presente" : "ausente")
                + "  \u2022  shaders: " + shaderText() + "\n"
                + "Driver: " + driverLabel() + "\n"
                + "Host com adrenotools. DM_VK_DRIVER so com driver escolhido.\n"
                + "Port nx2android, uso pessoal, com o dump proprio.");
        col.addView(info);
        col.addView(makeYtPill(), spacedParams());
        TextView logTitle = new TextView(this);
        logTitle.setTextSize(13);
        logTitle.setTextColor(0xFFC9A227);
        logTitle.setText("Log (ultimas 30 linhas):");
        col.addView(logTitle);
        TextView logTv = new TextView(this);
        logTv.setText(logTail);
        logTv.setTextSize(10);
        logTv.setTextColor(Color.WHITE);
        // Altura fixa, sem ScrollView: o que nao couber e truncado
        // na exibicao (o arquivo completo continua na pasta do jogo).
        LinearLayout.LayoutParams logLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(180));
        col.addView(logTv, logLp);
        themed(new AlertDialog.Builder(this).setTitle("Sobre").setView(col)
                .setPositiveButton("Fechar", null));
    }

    private void showImportDialog() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(8), dp(16), dp(8));
        Button folder = new Button(this);
        folder.setText("Importar pasta do jogo extraida");
        folder.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                confirmImport(new Runnable() {
                    @Override public void run() { pickTree(); }
                });
            }
        });
        styleDialogButton(folder); col.addView(folder, spacedParams());
        Button zip = new Button(this);
        zip.setText("Importar zip do data pack");
        zip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                confirmImport(new Runnable() {
                    @Override public void run() { pickZip(); }
                });
            }
        });
        styleDialogButton(zip); col.addView(zip, spacedParams());
        TextView note = new TextView(this);
        note.setTextSize(13);
        note.setTextColor(Color.WHITE);
        note.setText("Escolha a pasta/zip cujo conteudo e RomFS, shaders.dmsv e env.txt (nao a pasta-mae dela).");
        col.addView(note);
        themed(new AlertDialog.Builder(this).setTitle("Importar jogo").setView(col)
                .setPositiveButton("Fechar", null));
    }

    private void confirmImport(final Runnable action) {
        if (importing) {
            showImportPhase("Importacao em andamento...");
            return;
        }
        if (!hasRomFs() && !hasShaders() && !hasEnv()) {
            action.run();
            return;
        }
        themed(new AlertDialog.Builder(this)
                .setTitle("Importar dados")
                .setMessage("Ja existem dados do jogo nesta pasta. Importar vai "
                        + "substituir os arquivos com o mesmo nome. Continuar?")
                .setPositiveButton("Importar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        action.run();
                    }
                })
                .setNegativeButton("Cancelar", null));
    }

    private void pickTree() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(i, REQ_PICK_TREE);
    }

    private void pickZip() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        startActivityForResult(i, REQ_PICK_ZIP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQ_PICK_TREE) {
            final Uri tree = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(tree,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            copyTreeInBackground(tree);
        } else if (requestCode == REQ_PICK_ZIP) {
            copyZipInBackground(data.getData());
        } else if (requestCode == REQ_PICK_DRIVER_SO) {
            importDriverSo(data.getData());
        } else if (requestCode == REQ_PICK_DRIVER_ZIP) {
            importDriverZip(data.getData());
        }
    }

    private void listTree(Uri treeUri, String parentId, String rel,
                          List<DocEntry> out, ContentResolver cr) throws Exception {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId);
        Cursor c = null;
        try {
            c = cr.query(children, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE}, null, null, null);
            if (c == null) return;
            while (c.moveToNext()) {
                String id = c.getString(0);
                String name = c.getString(1);
                String mime = c.getString(2);
                long size = c.isNull(3) ? 0 : c.getLong(3);
                if (name == null || name.equals(".") || name.equals("..")) continue;
                DocEntry e = new DocEntry();
                e.uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id);
                e.rel = rel.isEmpty() ? name : rel + "/" + name;
                e.dir = DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
                e.size = size;
                out.add(e);
                if (e.dir) listTree(treeUri, id, e.rel, out, cr);
            }
        } finally {
            if (c != null) c.close();
        }
    }

    private void writeFile(InputStream in, File dest, long[] copied, long total, boolean closeIn) throws Exception {
        dest.getParentFile().mkdirs();
        File tmp = new File(dest.getParentFile(), dest.getName() + ".tmp");
        OutputStream out = new FileOutputStream(tmp);
        byte[] buf = new byte[262144];
        int n;
        try {
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
                copied[0] += n;
                postImportProgress(copied[0], total);
            }
        } finally {
            try { if (closeIn) in.close(); } catch (Exception ignored) {}
            try { out.close(); } catch (Exception ignored) {}
        }
        if (dest.exists() && !dest.delete()) {
            throw new java.io.IOException("Nao foi possivel substituir " + dest.getName());
        }
        if (!tmp.renameTo(dest)) {
            throw new java.io.IOException("Falha ao finalizar " + dest.getName());
        }
    }

    private void finishImport() {
        if (hasRomFs() && hasShaders() && hasEnv()) {
            postStatus("Copia concluida. Pronto para jogar.");
        } else {
            StringBuilder missing = new StringBuilder();
            if (!hasRomFs()) missing.append("RomFS/ ");
            if (!hasShaders()) missing.append("shaders.dmsv ");
            if (!hasEnv()) missing.append("env.txt ");
            postStatus("Copia concluida, mas ainda falta: " + missing.toString()
                    + ". Verifique se escolheu a pasta/zip cujo conteudo e RomFS, "
                    + "shaders.dmsv e env.txt.");
        }
    }

    private void ensureImportDialog() {
        if (importLoadingDialog != null && importLoadingDialog.isShowing()) return;
        LinearLayout col = new LinearLayout(DarkSoulsLauncherActivity.this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(16), dp(16), dp(16));
        importLoadingPct = new TextView(DarkSoulsLauncherActivity.this);
        importLoadingPct.setTextSize(24);
        importLoadingPct.setTypeface(null, android.graphics.Typeface.BOLD);
        importLoadingPct.setTextColor(0xFFC9A227);
        importLoadingPct.setText("\u2026");
        col.addView(importLoadingPct);
        LinearLayout track = new LinearLayout(DarkSoulsLauncherActivity.this);
        track.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setColor(0xFF333333);
        trackBg.setCornerRadius(dp(8));
        track.setBackground(trackBg);
        importLoadingFill = new View(DarkSoulsLauncherActivity.this);
        GradientDrawable fillBg = new GradientDrawable();
        fillBg.setColor(0xFF8C6A1F);
        float r = dp(8);
        fillBg.setCornerRadii(new float[]{r, r, 0, 0, 0, 0, r, r});
        importLoadingFill.setBackground(fillBg);
        importLoadingEmpty = new View(DarkSoulsLauncherActivity.this);
        track.addView(importLoadingFill, new LinearLayout.LayoutParams(0, dp(14), 0f));
        track.addView(importLoadingEmpty, new LinearLayout.LayoutParams(0, dp(14), 100f));
        col.addView(track);
        importLoadingDetail = new TextView(DarkSoulsLauncherActivity.this);
        importLoadingDetail.setTextSize(14);
        importLoadingDetail.setTextColor(Color.WHITE);
        col.addView(importLoadingDetail);
        importLoadingFile = new TextView(DarkSoulsLauncherActivity.this);
        importLoadingFile.setTextSize(13);
        importLoadingFile.setTextColor(0xFFFFCDD2);
        col.addView(importLoadingFile);
        importLoadingDialog = themed(new AlertDialog.Builder(DarkSoulsLauncherActivity.this)
                .setTitle("Importando...")
                .setView(col)
                .setCancelable(false));
    }

    private void showImportPhase(final String msg) {
        mainHandler.post(new Runnable() {
            @Override public void run() {
                ensureImportDialog();
                importLoadingPct.setText("\u2026");
                importLoadingDetail.setText(msg);
                importLoadingFile.setText("");
                importLoadingFill.setLayoutParams(new LinearLayout.LayoutParams(0, dp(14), 0f));
                importLoadingEmpty.setLayoutParams(new LinearLayout.LayoutParams(0, dp(14), 100f));
            }
        });
    }

    private void dismissImportLoading() {
        mainHandler.post(new Runnable() {
            @Override public void run() {
                if (importLoadingDialog != null && importLoadingDialog.isShowing()) {
                    importLoadingDialog.dismiss();
                }
                importLoadingDialog = null;
                importLoadingPct = null;
                importLoadingDetail = null;
                importLoadingFile = null;
                importLoadingFill = null;
                importLoadingEmpty = null;
            }
        });
    }

    private void postImportProgress(long copied, long total) {
        if (total <= 0) return;
        final int pct = (int) (copied * 100 / total);
        if (pct == importLastPct) return;
        importLastPct = pct;
        final String detail = (copied / 1024 / 1024) + " MB de " + (total / 1024 / 1024) + " MB";
        final String file = importCurrentFile;
        mainHandler.post(new Runnable() {
            @Override public void run() {
                ensureImportDialog();
                importLoadingPct.setText(pct + "%");
                importLoadingDetail.setText(detail);
                importLoadingFile.setText(file);
                importLoadingFill.setLayoutParams(new LinearLayout.LayoutParams(0, dp(14), pct));
                importLoadingEmpty.setLayoutParams(new LinearLayout.LayoutParams(0, dp(14), 100 - pct));
            }
        });
    }

    private void showImportError(final String msg) {
        mainHandler.post(new Runnable() {
            @Override public void run() {
                themed(new AlertDialog.Builder(DarkSoulsLauncherActivity.this)
                        .setTitle("Falha na importacao")
                        .setMessage(msg)
                        .setPositiveButton("Fechar", null));
            }
        });
    }

    private void setImporting(final boolean on) {
        importing = on;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                artLocked = on;
            }
        });
    }

    private void copyTreeInBackground(final Uri tree) {
        setImporting(true);
        showImportPhase("Lendo a pasta escolhida...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final File destRoot = dataDir();
                try {
                    ContentResolver cr = getContentResolver();
                    String rootId = DocumentsContract.getTreeDocumentId(tree);
                    List<DocEntry> entries = new ArrayList<DocEntry>();
                    listTree(tree, rootId, "", entries, cr);
                    boolean hasTopRomFs = false;
                    for (DocEntry e : entries) if (e.rel.equals("RomFS") && e.dir) hasTopRomFs = true;
                    if (!hasTopRomFs) {
                        throw new java.io.IOException("A pasta escolhida nao contem RomFS/ "
                                + "no topo. Escolha a pasta cujo conteudo e RomFS, "
                                + "shaders.dmsv e env.txt (nao a pasta-mae dela).");
                    }
                    long total = 0;
                    for (DocEntry e : entries) if (!e.dir) total += e.size;
                    if (total > 0) {
                        StatFs stat = new StatFs(destRoot.getAbsolutePath());
                        if (stat.getAvailableBytes() < total + 10L * 1024 * 1024) {
                            throw new java.io.IOException("Espaco livre insuficiente para "
                                    + (total / 1024 / 1024) + " MB.");
                        }
                    }
                    long[] copied = new long[]{0};
                    for (DocEntry e : entries) {
                        File dest = new File(destRoot, e.rel);
                        if (e.dir) {
                            dest.mkdirs();
                            continue;
                        }
                        InputStream in = cr.openInputStream(e.uri);
                        if (in == null) throw new java.io.IOException("Nao foi possivel abrir " + e.rel);
                        importCurrentFile = e.rel;
                        writeFile(in, dest, copied, total, true);
                    }
                    finishImport();
                } catch (final Exception ex) {
                    showImportError("Falha na copia: " + ex.getMessage());
                } finally {
                    dismissImportLoading();
                    setImporting(false);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            refreshPlay();
                        }
                    });
                }
            }
        }).start();
    }

    private void copyZipInBackground(final Uri zipUri) {
        setImporting(true);
        showImportPhase("Lendo o zip escolhido...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final File destRoot = dataDir();
                try {
                    InputStream raw = getContentResolver().openInputStream(zipUri);
                    if (raw == null) throw new java.io.IOException("Nao foi possivel abrir o zip.");
                    List<String> names = new ArrayList<String>();
                    ZipInputStream zin = new ZipInputStream(raw);
                    ZipEntry ze;
                    long total = 0;
                    while ((ze = zin.getNextEntry()) != null) {
                        String name = ze.getName();
                        if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                            throw new java.io.IOException("Entrada invalida no zip: " + name);
                        }
                        names.add(name);
                        if (ze.getSize() > 0) total += ze.getSize();
                    }
                    zin.close();
                    String prefix = "";
                    boolean allSharePrefix = !names.isEmpty();
                    String first = names.get(0);
                    int slash = first.indexOf('/');
                    if (slash > 0) {
                        String cand = first.substring(0, slash + 1);
                        for (String nm : names) if (!nm.startsWith(cand)) allSharePrefix = false;
                        if (allSharePrefix) prefix = cand;
                    }
                    boolean hasTopRomFs = false;
                    for (String nm : names) {
                        String rel = prefix.isEmpty() ? nm : nm.substring(prefix.length());
                        if (rel.equals("RomFS/") || rel.equals("RomFS")) hasTopRomFs = true;
                    }
                    if (!hasTopRomFs) {
                        throw new java.io.IOException("O zip nao contem RomFS/ no topo. "
                                + "Escolha o zip do data pack (conteudo: RomFS, "
                                + "shaders.dmsv, env.txt).");
                    }
                    if (total > 0) {
                        StatFs stat = new StatFs(destRoot.getAbsolutePath());
                        if (stat.getAvailableBytes() < total + 10L * 1024 * 1024) {
                            throw new java.io.IOException("Espaco livre insuficiente para "
                                    + (total / 1024 / 1024) + " MB.");
                        }
                    }
                    InputStream raw2 = getContentResolver().openInputStream(zipUri);
                    if (raw2 == null) throw new java.io.IOException("Nao foi possivel reabrir o zip.");
                    ZipInputStream zin2 = new ZipInputStream(raw2);
                    long[] copied = new long[]{0};
                    ZipEntry ze2;
                    while ((ze2 = zin2.getNextEntry()) != null) {
                        String name = ze2.getName();
                        String rel = prefix.isEmpty() ? name : name.substring(prefix.length());
                        if (rel.isEmpty()) continue;
                        File dest = new File(destRoot, rel);
                        if (ze2.isDirectory() || name.endsWith("/")) {
                            dest.mkdirs();
                            continue;
                        }
                        importCurrentFile = rel;
                        writeFile(zin2, dest, copied, total, false);
                        zin2.closeEntry();
                    }
                    zin2.close();
                    finishImport();
                } catch (final Exception ex) {
                    showImportError("Falha na copia: " + ex.getMessage());
                } finally {
                    dismissImportLoading();
                    setImporting(false);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            refreshPlay();
                        }
                    });
                }
            }
        }).start();
    }

    private void postStatus(final String text) {
        mainHandler.post(new Runnable() {
            @Override public void run() {
                Toast.makeText(DarkSoulsLauncherActivity.this, text, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
