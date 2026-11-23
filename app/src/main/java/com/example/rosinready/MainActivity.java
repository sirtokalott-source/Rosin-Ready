package com.example.rosinready;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private LinearLayout layoutApiKeySection, layoutStrainDetails, layoutLoading;
    private EditText etApiKey, etSmellProfile, etClaimedStrain, etWeight, etDiameter;
    private EditText etVirginYield, etSecondYield, etActualColor;
    private CheckBox cbVacPacked, cbCanHydrate, cbCheckStrain;
    private RadioGroup rgMoisture, rgOfflineVisual;
    private SeekBar sbTerps;
    private TextView tvTerpLabel, tvResults;
    private ImageView ivWholeBud, ivSplitBud, ivBagPhoto;
    private Button btnRunAssessment;

    private String base64Whole = null;
    private String base64Split = null;
    private String base64Bag = null;
    private int pickingTarget = 1; // 1 = Whole, 2 = Core, 3 = Bag

    private SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        try {
                            InputStream is = getContentResolver().openInputStream(uri);
                            Bitmap bitmap = BitmapFactory.decodeStream(is);
                            Bitmap scaled = scaleBitmap(bitmap, 1024);
                            String encoded = encodeToBase64(scaled);
                            if (pickingTarget == 1) {
                                base64Whole = encoded;
                                ivWholeBud.setImageBitmap(scaled);
                            } else if (pickingTarget == 2) {
                                base64Split = encoded;
                                ivSplitBud.setImageBitmap(scaled);
                            } else {
                                base64Bag = encoded;
                                ivBagPhoto.setImageBitmap(scaled);
                            }
                        } catch (Exception e) {
                            Toast.makeText(this, "Image error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("RosinReadyPrefs", MODE_PRIVATE);

        if (!prefs.contains("calibrated_batches")) {
            String seedBatch =
                    "- Batch 1: 6.0g (Rehydrated vac-pack, Terps 2.4/5, yellow core) -> 1st (83C/800 bag PSI): 0.73g, 2nd (91C/1000 bag PSI): 0.27g | Clean golden 1st, dark 2nd.\n" +
                    "- Batch 2: 6.4g (Rehydrated 10mm vac-pack, Terps 2.8/5) -> 1st (37u bag, 83C/800 bag PSI): 1.05g, 2nd (83C/1600 bag PSI): 0.20g | Clean golden both presses.\n" +
                    "- Batch 3: 5.8g (StarDawg, Boveda 62%, Terps 3.3/5) -> 1st (37u bag, ~80C/800 bag PSI, ~105-120s short cycle): 1.10g, 2nd (~1,000 bag PSI / 2,000 gauge PSI): 0.17g | Lovely golden translucent honey.\n" +
                    "- Batch 4: 6.0g (StarDawg, Boveda 62%, Terps 3.3/5) -> 1st (37u bag, ~80C/800 bag PSI, ~105-120s short cycle): 1.12g, 2nd (~1,000 bag PSI / 2,000 gauge PSI): 0.17g | Clean golden-amber honey, cured into velvety golden badder.\n" +
                    "- Batch 5: 13.0g split into two 6.5g bags (StarDawg, Boveda 62%, Terps 3.3/5) -> 1st (37u bag, ~80C/800 bag PSI): 2.30g, 2nd (~1,000 bag PSI / 2,000 gauge PSI): 0.47g | Thick golden-amber sap/batter.";
            prefs.edit().putString("calibrated_batches", seedBatch).apply();
        }
        layoutApiKeySection = findViewById(R.id.layoutApiKeySection);
        layoutStrainDetails = findViewById(R.id.layoutStrainDetails);
        layoutLoading = findViewById(R.id.layoutLoading);
        etApiKey = findViewById(R.id.etApiKey);
        etSmellProfile = findViewById(R.id.etSmellProfile);
        etClaimedStrain = findViewById(R.id.etClaimedStrain);
        etWeight = findViewById(R.id.etWeight);
        etDiameter = findViewById(R.id.etDiameter);
        etVirginYield = findViewById(R.id.etVirginYield);
        etSecondYield = findViewById(R.id.etSecondYield);
        etActualColor = findViewById(R.id.etActualColor);

        cbVacPacked = findViewById(R.id.cbVacPacked);
        cbCanHydrate = findViewById(R.id.cbCanHydrate);
        cbCheckStrain = findViewById(R.id.cbCheckStrain);
        rgMoisture = findViewById(R.id.rgMoisture);
        rgOfflineVisual = findViewById(R.id.rgOfflineVisual);
        sbTerps = findViewById(R.id.sbTerps);
        tvTerpLabel = findViewById(R.id.tvTerpLabel);
        tvResults = findViewById(R.id.tvResults);

        ivWholeBud = findViewById(R.id.ivWholeBud);
        ivSplitBud = findViewById(R.id.ivSplitBud);
        ivBagPhoto = findViewById(R.id.ivBagPhoto);
        btnRunAssessment = findViewById(R.id.btnRunAssessment);

        String savedKey = prefs.getString("gemini_api_key", "");
        etApiKey.setText(savedKey);
        if (!savedKey.isEmpty()) {
            layoutApiKeySection.setVisibility(View.GONE);
        }

        cbCheckStrain.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutStrainDetails.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (!isChecked) {
                etClaimedStrain.setText("");
                base64Bag = null;
                ivBagPhoto.setImageDrawable(null);
            }
        });

        findViewById(R.id.btnWholeBud).setOnClickListener(v -> { pickingTarget = 1; openGallery(); });
        findViewById(R.id.btnSplitBud).setOnClickListener(v -> { pickingTarget = 2; openGallery(); });
        findViewById(R.id.btnBagPhoto).setOnClickListener(v -> { pickingTarget = 3; openGallery(); });
        findViewById(R.id.btnBeginnerGuide).setOnClickListener(v -> showBeginnerGuide());

        setupTerpSlider();
        restoreBatchDraft();

        btnRunAssessment.setOnClickListener(v -> runAssessment());
        findViewById(R.id.btnLogOutcome).setOnClickListener(v -> savePressOutcome());
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveBatchDraft();
    }
    private void saveBatchDraft() {
        SharedPreferences.Editor ed = getSharedPreferences("RosinDraftPrefs", MODE_PRIVATE).edit();
        ed.putString("weight", etWeight.getText().toString());
        ed.putString("diameter", etDiameter.getText().toString());
        ed.putInt("terpsProgress", sbTerps.getProgress());
        ed.putString("smell", etSmellProfile.getText().toString());
        ed.putInt("moistureId", rgMoisture.getCheckedRadioButtonId());
        ed.putBoolean("vacPacked", cbVacPacked.isChecked());
        ed.putBoolean("canHydrate", cbCanHydrate.isChecked());
        ed.putBoolean("checkStrain", cbCheckStrain.isChecked());
        ed.putString("claimedStrain", etClaimedStrain.getText().toString());
        ed.putString("resultsText", tvResults.getText().toString());
        ed.apply();
    }

    private void restoreBatchDraft() {
        SharedPreferences d = getSharedPreferences("RosinDraftPrefs", MODE_PRIVATE);
        if (!d.contains("weight")) return;
        etWeight.setText(d.getString("weight", ""));
        etDiameter.setText(d.getString("diameter", ""));
        sbTerps.setProgress(d.getInt("terpsProgress", 20));
        etSmellProfile.setText(d.getString("smell", ""));
        int mId = d.getInt("moistureId", R.id.rbDry5);
        if (mId != -1) rgMoisture.check(mId);
        cbVacPacked.setChecked(d.getBoolean("vacPacked", true));
        cbCanHydrate.setChecked(d.getBoolean("canHydrate", false));
        boolean strainChecked = d.getBoolean("checkStrain", false);
        cbCheckStrain.setChecked(strainChecked);
        layoutStrainDetails.setVisibility(strainChecked ? View.VISIBLE : View.GONE);
        etClaimedStrain.setText(d.getString("claimedStrain", ""));
        String savedRes = d.getString("resultsText", "");
        if (!savedRes.isEmpty()) tvResults.setText(savedRes);
    }

    private void clearBatchDraftAndInputs() {
        getSharedPreferences("RosinDraftPrefs", MODE_PRIVATE).edit().clear().apply();
        etWeight.setText("");
        etDiameter.setText("");
        sbTerps.setProgress(20);
        etSmellProfile.setText("");
        rgMoisture.check(R.id.rbDry5);
        cbVacPacked.setChecked(true);
        cbCanHydrate.setChecked(false);
        cbCheckStrain.setChecked(false);
        layoutStrainDetails.setVisibility(View.GONE);
        etClaimedStrain.setText("");
        etVirginYield.setText("");
        etSecondYield.setText("");
        etActualColor.setText("");
        tvResults.setText("Load photos (or pick offline visual look) and enter batch details to run assessment.");
    }
    private void showBeginnerGuide() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1A1A1A);
        root.setPadding(40, 40, 40, 40);

        TextView title = new TextView(this);
        title.setText("AMATEUR PRESSING & HYDRATION GUIDE");
        title.setTextColor(0xFF4CAF50);
        title.setTextSize(18f);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, 24);
        root.addView(title);

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        TextView body = new TextView(this);
        body.setTextColor(0xFFEEEEEE);
        body.setTextSize(15f);
        body.setLineSpacing(10f, 1.25f);
        body.setPadding(0, 0, 12, 24);
        body.setText(
                "IMPORTANT DISCLAIMER:\n" +
                "All timers, temperatures, and pressures in this app are baseline guides—never a substitute for physical feel, visual flow observation, and hands-on experience.\n\n" +
                "1. HYGROMETER CALIBRATION (READ THIS FIRST)\n" +
                "• Digital vs. Analogue: A digital mini-hygrometer is recommended for accuracy, though the 68-68.5% true RH cutoff works for either.\n" +
                "• Salt Calibration Test: Place a bottle cap of table salt dampened with a few drops of water (wet sand texture) inside a sealed jar with your hygrometer for 12-24 hours at steady room temp (75% RH).\n\n" +
                "2. REHYDRATING DRY FLOWER (500ml JAR PROTOCOL)\n" +
                "• Stone Isolation: Soak a RAW Hydrostone in distilled water and isolate it on parchment or inside a small lid so wet terracotta NEVER touches flower.\n" +
                "• Mid-Run Rotation: Remove Hydrostone & hygrometer first, gently shake/roll the jar halfway through so glass-facing sides rotate up, then return stone.\n" +
                "• Swapping to Boveda: Pull stone at ~68-68.5% RH, drop in a 62% Boveda pack, and seal 48 hours (rolling jar every 12 hrs). Golden Rule: Water cannot replace lost terpenes (Level 1 hay is unsuitable).\n\n"
        );
        TextView body2 = new TextView(this);
        body2.setTextColor(0xFFEEEEEE);
        body2.setTextSize(15f);
        body2.setLineSpacing(10f, 1.25f);
        body2.setPadding(0, 0, 12, 24);
        body2.setText(
                "3. HOW 62% RH FEELS ALL THE WAY THROUGH\n" +
                "• Pin Moisture Meter: 10% to 12% internal moisture.\n" +
                "• Spring-Back & Stem Snap: Bud rebounds gently under squeeze; inner stem creases fibrously before snapping.\n\n" +
                "4. PRESSING PARAMETERS, FLOW CUTOFF & 2ND PRESS\n" +
                "• Why 37u Micron Bags: Tighter mesh blocks chlorophyll, lipids, and waxes for maximum purity.\n" +
                "• Visual Flow Cutoff + 30-45s Hold Rule: Watch bag edges—once oil stops actively running out, hold 30-45s max and lift plates (cuts combined ramp + hold ~1 min shorter on fresh, sticky flower).\n" +
                "• Golden 2nd-Press Pressure Rule: For clean gold 2nd press, use ~2,000 gauge PSI (~1,000 bag PSI) at the SAME temp as 1st press. Use ~3,200 gauge PSI (~1,600 bag PSI) + 91-102°C heat ONLY for dark/low-yield rescues.\n" +
                "• Thermal Ceilings: Stay below 105°C to prevent burnt taste; cannabinoids stay intact until ~120°C.\n\n" +
                "5. POST-PRESS CURING GUIDE & PACKAGING\n" +
                "• Cold Cure (24-48 hr @ 18-20°C): Seal in glass at cool room temp for 24-48 hrs until nucleation clouds the rosin, then gently fold.\n" +
                "• Soft Wet Badder (2.0-2.5 hr @ 38°C): Warm cure on heat plate/mat for 2.0-2.5 hrs, lift off, and whip/whack vigorously for glossy wet badder.\n" +
                "• Firmer Easy-Handling Budder (3.0-3.5 hr @ 38°C): Warm cure 3.0-3.5 hrs, move to cooling plate, and DO NOT whip—knead/fold only for stable budder.\n" +
                "• Dark UV Glass Pot & Cooldown: Cure in a dark UV-protected glass pot (or blind-wrapped jar) using a 2.5-3 hr blind timer, followed by a mandatory 45-60 min sealed cooldown before opening.\n" +
                "• Mylar Warning: Ignore 'Cali' Mylar bag graphics—authentic packs are heat-sealed above the tear notch with separate thermal UID stickers."
        );

        LinearLayout guideContent = new LinearLayout(this);
        guideContent.setOrientation(LinearLayout.VERTICAL);
        guideContent.addView(body);
        guideContent.addView(body2);
        scroll.addView(guideContent);
        root.addView(scroll);

        Button closeBtn = new Button(this);
        closeBtn.setText("CLOSE GUIDE");
        closeBtn.setBackgroundColor(0xFF2E7D32);
        closeBtn.setTextColor(0xFFFFFFFF);
        closeBtn.setPadding(0, 28, 0, 28);
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        root.addView(closeBtn);

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.94);
            int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.88);
            dialog.getWindow().setLayout(width, height);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0xFF1A1A1A));
        }
        dialog.show();
    }
    private void setupTerpSlider() {
        sbTerps.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                double val = 1.0 + (progress / 10.0);
                String desc;
                if (val < 1.8) desc = "Monoterpenes are gone. Smells like hay.";
                else if (val < 2.8) desc = "Just detectable / very weak smell.";
                else if (val < 3.8) desc = "Reasonable terp smell, not waking the neighbours.";
                else if (val < 4.6) desc = "Smells really good. Top gear when grown.";
                else desc = "Full Fresh Gods Vagina stinking of Terpenes.";
                tvTerpLabel.setText(String.format(Locale.US, "Terpene Intensity: %.1f - %s", val, desc));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private int getSelectedMoistureOption() {
        int id = rgMoisture.getCheckedRadioButtonId();
        if (id == R.id.rbDry1) return 1;
        if (id == R.id.rbDry2) return 2;
        if (id == R.id.rbDry3) return 3;
        if (id == R.id.rbDry4) return 4;
        return 5;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo active = cm != null ? cm.getActiveNetworkInfo() : null;
        return active != null && active.isConnected();
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void setAnalyzingState(boolean running) {
        layoutLoading.setVisibility(running ? View.VISIBLE : View.GONE);
        btnRunAssessment.setEnabled(!running);
    }
    private void runAssessment() {
        String apiKey = etApiKey.getText().toString().trim();
        if (!apiKey.isEmpty()) {
            prefs.edit().putString("gemini_api_key", apiKey).apply();
            layoutApiKeySection.setVisibility(View.GONE);
        }

        int moistureOpt = getSelectedMoistureOption();
        double terps = 1.0 + (sbTerps.getProgress() / 10.0);
        boolean isVacPacked = cbVacPacked.isChecked();
        boolean canHydrate = cbCanHydrate.isChecked();
        boolean checkStrain = cbCheckStrain.isChecked();
        String smell = etSmellProfile.getText().toString().trim();
        String strain = checkStrain ? etClaimedStrain.getText().toString().trim() : "";
        double weight = parseDoubleOrDefault(etWeight.getText().toString(), 3.5);
        double diameter = parseDoubleOrDefault(etDiameter.getText().toString(), 15.0);

        boolean canRunOnlineAi = isNetworkAvailable() && !apiKey.isEmpty() && base64Whole != null && base64Split != null;
        if (!canRunOnlineAi) {
            runOfflineFallback(moistureOpt, terps, weight, diameter, canHydrate, checkStrain,
                    !isNetworkAvailable() ? "Offline mode active (no internet connection)." :
                    apiKey.isEmpty() ? "Offline visual fallback used (enter Gemini API key above for AI photo scan)." :
                    "Offline visual fallback used (attach both Whole Bud & Inner Core photos for AI scan).");
            return;
        }

        setAnalyzingState(true);
        tvResults.setText("Analyzing trichome heads, core oxidation, and press physics... Please wait 1–2 mins.");

        executor.execute(() -> {
            try {
                String responseText = callGeminiVision(apiKey, moistureOpt, terps, isVacPacked, checkStrain, smell, strain, weight);
                String finalReport = processEngineMathAndBuildReport(responseText, moistureOpt, terps, weight, diameter, canHydrate, checkStrain);
                runOnUiThread(() -> {
                    setAnalyzingState(false);
                    tvResults.setText(finalReport);
                    saveBatchDraft();
                });
            } catch (GeminiHttpException ghe) {
                runOnUiThread(() -> {
                    setAnalyzingState(false);
                    boolean isAuthErr = (ghe.code == 400 || ghe.code == 401 || ghe.code == 403)
                            || (ghe.body != null && ghe.body.contains("API_KEY"));
                    if (isAuthErr) {
                        layoutApiKeySection.setVisibility(View.VISIBLE);
                        tvResults.setText("API Key rejected (HTTP " + ghe.code + "). Check your key above.");
                    } else {
                        layoutApiKeySection.setVisibility(View.GONE);
                        tvResults.setText("Gemini server busy (HTTP " + ghe.code + "). Your API key is safe—tap Run Assessment again.");
                    }
                    saveBatchDraft();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setAnalyzingState(false);
                    layoutApiKeySection.setVisibility(View.GONE);
                    if (e instanceof UnknownHostException || e instanceof SocketTimeoutException || e instanceof IOException) {
                        runOfflineFallback(moistureOpt, terps, weight, diameter, canHydrate, checkStrain,
                                "DNS / Wi-Fi drop detected—automatically switched to Offline Visual Selector.");
                    } else {
                        tvResults.setText("Scan interrupted: " + e.getMessage());
                        saveBatchDraft();
                    }
                });
            }
        });
    }

    private void runOfflineFallback(int moistureOpt, double terps, double weight, double diameter,
                                    boolean canHydrate, boolean checkStrain, String reasonNote) {
        int offId = rgOfflineVisual.getCheckedRadioButtonId();
        if (offId == -1) {
            rgOfflineVisual.check(R.id.rbOffMed);
            offId = R.id.rbOffMed;
        }
        double gen = (offId == R.id.rbOffHigh) ? 8.5 : (offId == R.id.rbOffMed) ? 7.0 : 5.0;
        double post = (offId == R.id.rbOffHigh) ? 8.0 : (offId == R.id.rbOffMed) ? 6.8 : 5.0;
        try {
            JSONObject fallbackJson = new JSONObject()
                    .put("genetic_head_score", gen)
                    .put("post_harvest_score", post)
                    .put("structure_score", 7.8)
                    .put("head_analysis", reasonNote + " Visual trichome tier applied.")
                    .put("genetics_flag", checkStrain ? "OFFLINE_SKIPPED" : "SKIPPED")
                    .put("genetics_notes", "Internet connection and photos required for AI strain & bag inspection.");
            tvResults.setText(processEngineMathAndBuildReport(fallbackJson.toString(), moistureOpt, terps, weight, diameter, canHydrate, checkStrain));
            saveBatchDraft();
        } catch (Exception e) {
            tvResults.setText("Offline error: " + e.getMessage());
        }
    }
    private static class GeminiHttpException extends Exception {
        final int code;
        final String body;
        GeminiHttpException(int code, String body) { super("HTTP " + code); this.code = code; this.body = body; }
    }

    private String callGeminiVision(String apiKey, int moistureOpt, double terps, boolean isVac, boolean checkStrain,
                                    String smell, String strain, double weight) throws Exception {
        String baseHost = "https://" + "generativelanguage." + "googleapis.com";
        String apiPath = "/v1beta/models/gemini-3.8-flash:generateContent";
        URL url = new URL(baseHost + apiPath);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(25000);
        conn.setReadTimeout(120000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("x-goog-api-key", apiKey);
        conn.setDoOutput(true);

        String history = prefs.getString("calibrated_batches", "");
        String prompt = "You are a solventless flower rosin press analyst. Inspect the Whole Bud and Inner Core photos. " +
                "Return ONLY valid JSON with keys: genetic_head_score (1-10), post_harvest_score (1-10), structure_score (1-10), " +
                "head_analysis (string), genetics_flag (string), genetics_notes (string). " +
                "Context: Weight=" + weight + "g, MoistureTier=" + moistureOpt + "/5, Terps=" + terps + "/5, VacPacked=" + isVac +
                ", Smell=" + smell + ", CheckStrain=" + checkStrain + ", ClaimedStrain=" + strain + ".\nCalibrated History:\n" + history;

        JSONArray parts = new JSONArray().put(new JSONObject().put("text", prompt));
        parts.put(new JSONObject().put("inline_data", new JSONObject().put("mime_type", "image/jpeg").put("data", base64Whole)));
        parts.put(new JSONObject().put("inline_data", new JSONObject().put("mime_type", "image/jpeg").put("data", base64Split)));
        if (checkStrain && base64Bag != null) {
            parts.put(new JSONObject().put("inline_data", new JSONObject().put("mime_type", "image/jpeg").put("data", base64Bag)));
        }

        JSONObject payload = new JSONObject().put("contents", new JSONArray().put(new JSONObject().put("parts", parts)));
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();

        if (code < 200 || code >= 300) throw new GeminiHttpException(code, sb.toString());

        JSONObject root = new JSONObject(sb.toString());
        String rawText = root.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");
        return rawText.replace("```json", "").replace("```", "").trim();
    }
    private String processEngineMathAndBuildReport(String jsonStr, int moistureOpt, double terps, double weight,
                                                   double diameter, boolean canHydrate, boolean checkStrain) throws Exception {
        JSONObject j = new JSONObject(jsonStr);
        double genetic = j.optDouble("genetic_head_score", 7.0);
        double postHarvest = j.optDouble("post_harvest_score", 7.0);
        double structure = j.optDouble("structure_score", 7.5);
        String headAnalysis = j.optString("head_analysis", "");
        double effectiveMoisture = (moistureOpt < 4 && canHydrate) ? 4.8 : moistureOpt;

        double score = ((genetic / 10.0) * 25.0) + ((postHarvest / 10.0) * 25.0) +
                       ((structure / 10.0) * 10.0) + ((effectiveMoisture / 5.0) * 25.0) + ((terps / 5.0) * 15.0);

        String tier, pressSection;
        boolean freshSticky = (terps >= 3.2 && moistureOpt >= 4);
        String cycleTiming = freshSticky
                ? "Short Cycle (~105-120s total: 30-45s pre-warm, 45s ramp, 30-45s hold max once oil stops running out — ~1 min shorter combined ramp + hold for fresh/sticky flower)"
                : "Full Cycle (~300s total: 90s pre-warm, 120s slow ramp, 30-45s hold after visual oil flow stops)";

        if (terps < 1.8) {
            score = Math.min(score, 55.0);
            tier = "NOT SUITABLE FOR PRESSING — VISCOSITY LOCK";
            pressSection = "2. VERDICT & CUTOFF:\nMonoterpenes gone (hay). Do NOT rehydrate for pressing; oil locks inside the 37u bag.";
        } else if (genetic < 6.0) {
            score = Math.min(score, 58.0);
            tier = "NOT SUITABLE FOR PRESSING — POOR TRICHOME HEADS";
            pressSection = "2. VERDICT & CUTOFF:\nInsufficient intact resin heads to overcome fixed 37u filter-bag absorption (~0.15-0.20g).";
        } else if (terps < 2.8) {
            tier = "BORDERLINE — FLAVOUR, CONSISTENCY & YIELD";
            pressSection = "2. VERDICT & GUIDANCE:\nLevel 2 terpene zone. Expect borderline flavour, consistency, and yield.";
        } else if (moistureOpt <= 2 && !canHydrate) {
            score = Math.min(score, 62.0);
            tier = "NOT SUITABLE AS-IS — CELLULOSE SPONGE LOSS";
            pressSection = "2. VERDICT & CUTOFF:\nDry (<54% RH) without hydration traps oil in plant fiber. Hydrate to 62% RH first.";
        } else {
            tier = score >= 78.0 ? "PRIME FOR PRESSING — HIGH-GRADE ROSIN CANDIDATE" : "GOOD TO PRESS — SOLID EXTRACTION";
            pressSection = "2. VERDICT & GUIDANCE:\nReady for 37u bottle-tech press. Break buds evenly (" + String.format(Locale.US, "%.0f", diameter) + "mm target).";
        }

        double estPct = Math.max(6.0, Math.min(26.0, (score / 100.0) * 24.5));
        double estGrams = weight * (estPct / 100.0);

        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.US, "1. READINESS SCORE: %.0f/100\n%s\n%s\n\n", score, tier, headAnalysis));
        out.append(pressSection).append("\n\n");
        out.append(String.format(Locale.US, "3. PREDICTED TOTAL YIELD:\n~%.2fg (%.1f%% on %.1fg input)\n\n", estGrams, estPct, weight));
        out.append("4. PRESS PROTOCOL & 2ND PRESS RULE:\n")
           .append("• 1st Press: 37u bag, ~80-83°C, ~800 bag PSI (~1,600 gauge PSI).\n")
           .append("• Visual Flow Cutoff: ").append(cycleTiming).append("\n")
           .append("• Golden 2nd Press: ~2,000 gauge PSI (~1,000 bag PSI) at SAME temp for clean gold. Use ~3,200 gauge PSI (~1,600 bag PSI) + 91-102°C heat ONLY for dark/low-yield rescues.\n\n");
        out.append("5. POST-PRESS CURING GUIDE:\n")
           .append("• Cold Cure (24-48 hr @ 18-20°C): Seal in glass at room temp until cloudy, then gently fold.\n")
           .append("• Soft Wet Badder (2.0-2.5 hr @ 38°C): Warm cure 2.0-2.5 hrs, lift off, and whip/whack for glossy wet badder.\n")
           .append("• Firmer Budder (3.0-3.5 hr @ 38°C): Warm cure 3.0-3.5 hrs, move to cooling plate, DO NOT whip—knead/fold only.\n")
           .append("• UV Jar & Cooldown: Use a dark UV glass pot with a 2.5-3 hr blind timer + mandatory 45-60 min sealed cooldown.");
        if (checkStrain) {
            out.append("\n\n6. STRAIN / PACKAGING CHECK:\n").append(j.optString("genetics_flag", "CHECKED"))
               .append(": ").append(j.optString("genetics_notes", ""));
        }
        return out.toString();
    }

    private void savePressOutcome() {
        String vYield = etVirginYield.getText().toString().trim();
        String sYield = etSecondYield.getText().toString().trim();
        String colorQual = etActualColor.getText().toString().trim();
        String weight = etWeight.getText().toString().trim();
        String smell = etSmellProfile.getText().toString().trim();
        double terps = 1.0 + (sbTerps.getProgress() / 10.0);

        if (vYield.isEmpty()) {
            Toast.makeText(this, "Enter at least 1st Press Yield (g) to save", Toast.LENGTH_SHORT).show();
            return;
        }

        String entry = String.format(Locale.US, "- Batch: %sg (%s, Terps %.1f/5) -> 1st: %sg, 2nd: %sg | Color/Quality: %s",
                weight.isEmpty() ? "?" : weight, smell, terps, vYield, sYield.isEmpty() ? "0" : sYield, colorQual);

        String existing = prefs.getString("calibrated_batches", "");
        String[] lines = existing.split("\n");
        StringBuilder updated = new StringBuilder(entry);
        int count = 1;
        for (String line : lines) {
            if (!line.trim().isEmpty() && count < 50) {
                updated.append("\n").append(line);
                count++;
            }
        }
        prefs.edit().putString("calibrated_batches", updated.toString()).apply();
        clearBatchDraftAndInputs();
        Toast.makeText(this, "Saved to engine memory & batch form cleared!", Toast.LENGTH_LONG).show();
    }

    private Bitmap scaleBitmap(Bitmap bmp, int maxDim) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        if (w <= maxDim && h <= maxDim) return bmp;
        float ratio = (float) w / h;
        return Bitmap.createScaledBitmap(bmp, w > h ? maxDim : Math.round(maxDim * ratio), h > w ? maxDim : Math.round(maxDim / ratio), true);
    }

    private String encodeToBase64(Bitmap bmp) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, baos);
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
    }

    private double parseDoubleOrDefault(String text, double fallback) {
        try { return Double.parseDouble(text.trim()); } catch (Exception e) { return fallback; }
    }
}
