package com.example.rosinready;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.Window;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private EditText etApiKey, etSmellProfile, etClaimedStrain, etWeight, etDiameter;
    private EditText etVirginYield, etSecondYield, etActualColor;
    private CheckBox cbVacPacked, cbCanHydrate, cbCheckStrain;
    private RadioGroup rgMoisture;
    private SeekBar sbTerps;
    private TextView tvTerpLabel, tvResults;
    private ImageView ivWholeBud, ivSplitBud;

    private String base64Whole = null;
    private String base64Split = null;
    private boolean pickingWhole = true;

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
                            if (pickingWhole) {
                                base64Whole = encoded;
                                ivWholeBud.setImageBitmap(scaled);
                            } else {
                                base64Split = encoded;
                                ivSplitBud.setImageBitmap(scaled);
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
            String seedBatch = "- Batch 1: 6.0g (Rehydrated vac-pack, Terps 2.4/5, yellow core) -> " +
                    "1st (83C/800 bag PSI): 0.73g, 2nd (91C/1000 bag PSI): 0.27g | Clean golden 1st, dark 2nd.\n" +
                    "- Batch 2: 6.4g (Rehydrated 10mm vac-pack, Terps 2.8/5) -> " +
                    "1st (37u bag, 83C/800 bag PSI): 1.05g, 2nd (83C/1600 bag PSI): 0.20g | Clean golden both presses.";
            prefs.edit().putString("calibrated_batches", seedBatch).apply();
        }

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
        sbTerps = findViewById(R.id.sbTerps);
        tvTerpLabel = findViewById(R.id.tvTerpLabel);
        tvResults = findViewById(R.id.tvResults);

        ivWholeBud = findViewById(R.id.ivWholeBud);
        ivSplitBud = findViewById(R.id.ivSplitBud);

        etApiKey.setText(prefs.getString("gemini_api_key", ""));

        cbCheckStrain.setOnCheckedChangeListener((buttonView, isChecked) -> {
            etClaimedStrain.setEnabled(isChecked);
            etClaimedStrain.setHintTextColor(isChecked ? 0xFF888888 : 0xFF555555);
            if (!isChecked) etClaimedStrain.setText("");
        });

        findViewById(R.id.btnWholeBud).setOnClickListener(v -> {
            pickingWhole = true;
            openGallery();
        });

        findViewById(R.id.btnSplitBud).setOnClickListener(v -> {
            pickingWhole = false;
            openGallery();
        });

        findViewById(R.id.btnBeginnerGuide).setOnClickListener(v -> showBeginnerGuide());
        setupTerpSlider();

        findViewById(R.id.btnRunAssessment).setOnClickListener(v -> runAssessment());
        findViewById(R.id.btnLogOutcome).setOnClickListener(v -> savePressOutcome());
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
                "All timers, temperatures, and pressures in this app are baseline guides for inexperienced users—never a substitute for physical feel, observation, and hands-on experience.\n\n" +
                "1. HYGROMETER CALIBRATION (READ THIS FIRST)\n" +
                "• Digital vs. Analogue: A digital mini-hygrometer is recommended for accuracy and because there is no mechanical coil to stick, though the 68-68.5% true RH cutoff works for either.\n" +
                "• Salt Calibration Test (If Using an Analogue Hygrometer): Place a bottle cap of table salt dampened with a few drops of water (wet sand texture, not dissolved) inside a sealed zip-lock bag or jar with your hygrometer for 12-24 hours at steady room temp. It creates an exact 75% RH environment—check the back of the dial for a small calibration screw to set the needle directly to 75%, or note the offset.\n\n" +
                "2. REHYDRATING DRY FLOWER (500ml JAR PROTOCOL)\n" +
                "• Why Hydrate: Pressing dry bud (<54% RH) turns plant fiber into a sponge that traps oil inside the 37u bag.\n" +
                "• Stone Isolation: Soak a RAW Hydrostone in distilled water and isolate it on parchment paper or inside a small jar lid inside a 500ml jar so wet terracotta NEVER touches flower directly.\n" +
                "• Mid-Run Rotation (Cuts Core Wait in Half): Buds resting against the glass floor get zero vapor on their underside. At the halfway check, gently shake/roll the jar so dry glass-facing sides rotate up. WARNING: ALWAYS REMOVE THE HYDROSTONE AND HYGROMETER BEFORE SHAKING, then place them back in to finish.\n" +
                "• Swapping to Boveda (48-Hour Standard & Check Schedule): Pull the stone (true headspace RH typically peaks around 68-68.5% RH), give one gentle shake before dropping in a 62% Boveda pack, and seal for 48 hours. Gently roll the jar every 12 hours to rotate glass-contact sides up. Check tactile feel at 24 hours and every 12 hours after—if buds still feel too dry, put the Hydrostone back in for a short top-up run. Hygrometers (especially analogue dials due to lag) are guides only; readiness is strictly on you to verify by feel and stem snap.\n" +
                "• Two-Step Split Option: For bone-dry flower or cautious users, split the total Hydrostone time into two halves with an overnight sealed rest in between.\n" +
                "• Golden Rule: Water cannot replace lost terpenes. If it smells like dead hay (Level 1), it is not suitable for pressing.\n\n" +
                "3. HOW 62% RH FEELS ALL THE WAY THROUGH\n" +
                "• Gold Standard Tool (~£15-£25): A 2-prong pin moisture meter pressed into a thick bud reads 10% to 12% internal moisture at true 62% RH.\n" +
                "• Spring-Back Test: Gently squeeze a thicker bud. At 62% all through, it feels slightly soft and rebounds under gentle pressure. On dense vac-packed flower, a firmer squash compresses it slightly flatter permanently, but it should still bounce back softly from that compressed shape when pressed gently.\n" +
                "• Stem Bend-and-Snap: Bend an inner stem near the base—at 62% core equilibrium, the stem bends and creases fibrously first, then snaps (not a brittle matchstick click).\n\n" +
                "4. PRESSING PARAMETERS, RECOVERY & BAG PSI\n" +
                "• Why 37u Micron Bags: A 37u bag is a tighter mesh than standard flower pressing, used intentionally to achieve absolute purity by blocking chlorophyll, plant lipids, and waxes from coming through so you get the cleanest possible rosin.\n" +
                "• Two-Tier 2nd Press Recovery: If your 1st press looks clean/golden but leaves oil behind, increase PRESSURE ONLY (from 800 up to 1,600 bag PSI) at the same temp (e.g., 83°C) to recover clean golden rosin. If the 1st press is already dark brown with a terrible yield, increase BOTH pressure (up to 1,600 bag PSI) and temp—use 91°C first (retains better taste), or up to 102°C max for stubborn material.\n" +
                "• Thermal Ceilings (105°C Burnt Taste / 120°C Cannabinoids): Stay below 105°C to avoid adding a harsh burnt taste. Cannabinoids DO NOT degrade/burn out until ~120°C—so turning up to 91-102°C to rescue yield darkens color and reduces terpenes, while preserving cannabinoid potency.\n" +
                "• Gauge PSI vs. Actual Bag (Puck) PSI: Gauge readings vary across different presses depending on hydraulic ram piston size. Calculate actual puck pressure using: Total Ram Force = Gauge PSI × Piston Area (π × piston radius²), then Bag PSI = Total Ram Force ÷ Pressed Puck Area (π × puck radius²). For example, on a standard 7-ton press (~1.5\" ram piston = ~1.77 sq in) pressing a ~2.1\" bottle-tech puck (~3.5 sq in), 1,600 gauge PSI = ~800 bag PSI, and 3,200 gauge PSI = ~1,600 bag PSI.\n\n" +
                "5. POST-PRESS CURING & PACKAGING WARNING\n" +
                "• Cold Cure (Loud Terps 3.5+): Seal in glass at room temp (18-20°C) for 24-48 hrs and whip.\n" +
                "• Warm-Plate Cure (Borderline Terps 2.0-2.5): Seal in glass on a 38°C plate for 2-6 hrs until cloudy, cool, and give a gentle fold (do not whip hard) to nucleate into smooth, non-harsh golden pate budder.\n" +
                "• Mylar Bag Warning: Ignore 'Cali' Mylar bag art and printed THC numbers—authentic packs are heat-sealed above the tear notch with separate thermal UID stickers."
        );
        scroll.addView(body);
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
                else if (val < 3.8) desc = "Reasonable terp smell, not waking neighbours.";
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

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void runAssessment() {
        String apiKey = etApiKey.getText().toString().trim();
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Enter your Gemini API Key first", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.edit().putString("gemini_api_key", apiKey).apply();

        if (base64Whole == null || base64Split == null) {
            Toast.makeText(this, "Select both Whole Bud and Cross-Section photos", Toast.LENGTH_SHORT).show();
            return;
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

        tvResults.setText("Analyzing trichome heads, core oxidation, and press physics...");

        executor.execute(() -> {
            try {
                String responseText = callGeminiVision(apiKey, moistureOpt, terps, isVacPacked, checkStrain, smell, strain, weight);
                String finalReport = processEngineMathAndBuildReport(responseText, moistureOpt, terps, weight, diameter, canHydrate, checkStrain);
                runOnUiThread(() -> tvResults.setText(finalReport));
            } catch (Exception e) {
                runOnUiThread(() -> tvResults.setText("Error: " + e.getMessage()));
            }
        });
    }
    private String callGeminiVision(String apiKey, int moistureOpt, double terps, boolean isVac, boolean checkStrain,
                                    String smell, String strain, double weight) throws Exception {
        String baseHost = "https://generativelanguage.googleapis.com";
        String apiPath = "/v1beta/models/gemini-2.5-flash:generateContent";
URL url = new URL(baseHost + apiPath);

HttpURLConnection conn = (HttpURLConnection) url.openConnection();
conn.setRequestMethod("POST");
conn.setRequestProperty("Content-Type", "application/json");
conn.setRequestProperty("x-goog-api-key", apiKey);
conn.setDoOutput(true);

        String pastHistory = prefs.getString("calibrated_batches", "No past batches logged yet.");
        String strainRule = checkStrain
                ? "3. OPTIONAL STRAIN CHECK ACTIVE: Cross-check ClaimedStrain='" + strain + "' against Whole Bud photo morphology AND Smell='" + smell + "'. " +
                  "State confidence level (Low/Moderate/High Confidence) and note counterfeit Mylar bag tells (unsealed top above tear notch, THC % printed directly on bag art instead of thermal UID sticker)."
                : "3. OPTIONAL STRAIN CHECK DISABLED: Set genetics_flag to 'SKIPPED' and genetics_notes to 'Skipped.'";

        String prompt = "You are an empirical botanical resin-assessment engine for rosin extraction. " +
                "Image 1 is the whole exterior bud. Image 2 is the broken cross-section (may be fractured into multiple dry pieces facing camera).\n" +
                "Inputs: VacPacked=" + isVac + ", MoistureOption=" + moistureOpt + "/5, Terps=" + terps + "/5, Smell='" + smell + "', Weight=" + weight + "g.\n" +
                "Verified Past User Press History (Engine continuously calibrates to these logged outcomes):\n" + pastHistory + "\n\n" +
                "RULES:\n" +
                "1. Separate GENETIC_HEAD_SCORE (0-10: trichome density, bulbous head-to-stalk ratio, maturity) from " +
                "POST_HARVEST_SCORE (0-10: interior core oxidation/browning; if VacPacked=true, discount outer flattening and grade interior head survival across all upturned pieces).\n" +
                "2. Score STRUCTURE_SCORE (0-10: calyx density, low excess leaf, clean stem).\n" + strainRule + "\n" +
                "Return ONLY valid JSON: {\"genetic_head_score\": 8.5, \"post_harvest_score\": 7.0, \"structure_score\": 8.0, " +
                "\"head_analysis\": \"...\", \"genetics_flag\": \"VERIFIED_CONSISTENT | SUSPECT_MISMATCH | SKIPPED\", \"genetics_notes\": \"...\"}";

        JSONObject textPart = new JSONObject().put("text", prompt);
        JSONObject img1Part = new JSONObject().put("inline_data", new JSONObject().put("mime_type", "image/jpeg").put("data", base64Whole));
        JSONObject img2Part = new JSONObject().put("inline_data", new JSONObject().put("mime_type", "image/jpeg").put("data", base64Split));

        JSONArray parts = new JSONArray().put(textPart).put(img1Part).put(img2Part);
        JSONObject body = new JSONObject().put("contents", new JSONArray().put(new JSONObject().put("parts", parts)));

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes("UTF-8"));
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();

        JSONObject root = new JSONObject(sb.toString());
        String rawText = root.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");

        return rawText.replace("```json", "").replace("```", "").trim();
    }
    private String processEngineMathAndBuildReport(String jsonStr, int moistureOpt, double terps,
                                                   double weight, double diameter, boolean canHydrate, boolean checkStrain) throws Exception {
        JSONObject data = new JSONObject(jsonStr);
        double genetic = data.getDouble("genetic_head_score");
        double postHarvest = data.getDouble("post_harvest_score");
        double structure = data.getDouble("structure_score");

        double rawMoistureScore = (moistureOpt == 1) ? 1.0 : (moistureOpt == 2) ? 1.5 : (moistureOpt == 3) ? 2.1 : (moistureOpt == 4) ? 2.6 : 3.0;
        double effectiveMoisture = (moistureOpt <= 4 && canHydrate && terps >= 2.8) ? 2.9 : rawMoistureScore;

        double score = ((genetic / 10.0) * 25.0) + ((postHarvest / 10.0) * 25.0) +
                       ((structure / 10.0) * 10.0) + ((effectiveMoisture / 5.0) * 25.0) + ((terps / 5.0) * 15.0);

        String tier;
        String pressSection;

        if (terps < 1.8) {
            score = Math.min(score, 55.0);
            tier = "NOT SUITABLE FOR PRESSING — VISCOSITY LOCK";
            pressSection = "2. VERDICT & CUTOFF REASON:\nNot suitable for pressing. Monoterpenes are gone (hay). Do NOT rehydrate for pressing; adding water without terpene solvents yields dark, sluggish oil trapped inside the 37u bag.";
        } else if (genetic < 6.0) {
            score = Math.min(score, 58.0);
            tier = "NOT SUITABLE FOR PRESSING — POOR TRICHOME HEADS";
            pressSection = "2. VERDICT & CUTOFF REASON:\nNot suitable for pressing. Insufficient intact resin heads to overcome fixed 37u filter-bag absorption (~0.15-0.20g), resulting in near-zero return.";
        } else if (terps < 2.8) {
            tier = "BORDERLINE — FLAVOUR, CONSISTENCY & YIELD";
            pressSection = "2. VERDICT & GUIDANCE:\nTerpene intensity is in the Level 2 zone. Expect borderline results on flavour, consistency, and yield.";
        } else {
            if ((moistureOpt == 1 || moistureOpt == 2) && !canHydrate) {
                score = Math.min(score, 62.0);
                tier = "NOT SUITABLE AS-IS — CELLULOSE SPONGE LOSS";
                pressSection = "2. VERDICT & CUTOFF REASON:\nNot suitable for pressing dry (<54% RH) with 'I Can Hydrate' unchecked. Pressing now traps oil inside dry plant fiber (~5-6% blowout yield). Hydrate to 62% RH first.";
            } else if (moistureOpt == 3 && !canHydrate) {
                score = Math.min(score, 68.0);
                tier = "SUBSTANDARD — DIMINISHED RETURN WARNING";
                pressSection = "2. VERDICT & GUIDANCE:\nFlower is still dry despite slight stickiness. Returns will be diminished—possibly quite badly—due to partial sponge absorption. Hydrating up to 62% RH first is recommended.";
            } else if (moistureOpt == 4) {
                tier = score >= 75.0 ? "SUITABLE FOR PRESSING (BOVEDA 62% TOP-UP)" : "RECOVERABLE PRESS (BOVEDA 62% TOP-UP)";
                pressSection = String.format(Locale.US,
                        "2. HYDRATION & PRESS PARAMETERS:\n• Boveda-Only Rule: Because flower already holds sticky resin, use 62%% Boveda packs ONLY for 24-48 hrs (NO Hydrostone needed).\n" +
                        "• Bag & Temp: 37u micron bag (a tighter mesh used intentionally for absolute purity to block chlorophyll, lipids, and waxes) | 77-80C at ~800 bag PSI -> Est. %.2fg - %.2fg single-press yield (<=0.1g left for 2nd press).",
                        weight * 0.19, weight * 0.25);
            } else {
                boolean needsStone = (moistureOpt <= 3);
                tier = (score >= 75.0) ? (needsStone ? "SUITABLE FOR PRESSING (HYDRATE FIRST)" : "SUITABLE FOR PRESSING — PRIME SINGLE-PRESS")
                                       : (needsStone ? "DIMINISHED / RECOVERABLE PRESS (HYDRATE FIRST)" : "DIMINISHED / RECOVERABLE PRESS");
                String hydrationPlan = needsStone ? buildHydrostoneGuide(weight, diameter, moistureOpt) : "• Moisture: Optimal (~62% RH). Ready to press immediately.";
                pressSection = String.format(Locale.US,
                        "2. HYDRATION & PRESS PARAMETERS:\n%s\n" +
                        "• Bag & Temp: 37u micron bag (a tighter mesh used intentionally for absolute purity to block chlorophyll, lipids, and waxes) | 77-80C (loud fresh) or 81-83C (aged/yellow core) at ~800 bag PSI -> Est. %.2fg - %.2fg virgin yield.\n" +
                        "• 2nd Press Recovery: If 1st press is clean gold, increase PRESSURE ONLY (from 800 up to 1,600 bag PSI; e.g., ~1,600 to ~3,200 gauge PSI on a 7-ton press, though gauge readings vary by ram piston size) at the same temp (e.g., 83C). If 1st press is dark brown with a terrible yield, increase BOTH pressure (up to 1,600 bag PSI) and temp to 91C (keeps better taste) or up to 102C max (stay below 105C burnt taste; cannabinoids do not degrade until ~120C).\n" +
                        "• Post-Press Cure: Cold cure loud terps (24-48h) or warm-plate borderline pull-and-snap at 38C (2-6h) + gentle fold into golden pate budder.",
                        hydrationPlan, weight * 0.16, weight * 0.24);
            }
        }

        if (weight < 3.5 && terps >= 1.8 && genetic >= 6.0) {
            pressSection += "\n• Weight Warning: Under 3.5g incurs noticeable fixed 37u filter-bag absorption (~0.15-0.20g).";
        }

        StringBuilder report = new StringBuilder();
        report.append(String.format(Locale.US, "SUITABILITY SCORE: %.1f / 100\nVERDICT: %s\n\n", score, tier));
        report.append(String.format(Locale.US,
                "1. RESIN HEAD & CORE ANALYSIS:\n%s\n(Genetic Heads: %.1f/10 | Preservation: %.1f/10 | Structure: %.1f/10)\n\n",
                data.getString("head_analysis"), genetic, postHarvest, structure));
        report.append(pressSection);

        if (checkStrain) {
            report.append(String.format(Locale.US, "\n\n3. OPTIONAL STRAIN & LABEL CHECK [%s]:\n%s",
                    data.getString("genetics_flag"), data.getString("genetics_notes")));
        }
        return report.toString();
    }

    private String buildHydrostoneGuide(double weightGrams, double diameterMm, int moistureOpt) {
        double drynessFactor = (moistureOpt == 1) ? 1.25 : (moistureOpt == 2) ? 1.15 : 0.75;
        double baseMinutes = 55.0 + (weightGrams * 13.9);
        double sizeMultiplier = 0.875 + (diameterMm / 100.0);

        int totalMinutes = (int) Math.round(baseMinutes * drynessFactor * sizeMultiplier);
        totalMinutes = Math.max(30, Math.min(360, totalMinutes));

        return String.format(Locale.US,
                "• 500ml Jar Estimate: Isolated soaked RAW Hydrostone (on parchment/lid) for ~%d mins (%.1f hrs — can be split into 2 runs overnight), then remove stone (true headspace RH peaks ~68-68.5%%), shake gently, and seal with 62%% Boveda for 48 hrs.\n" +
                "• Check & Roll Schedule: Inspect squish & rotate jar at ~%d mins (remove stone & hygrometer before shaking). During Boveda equalization, gently roll the jar every 12 hrs and check tactile feel at 24 hrs, then every 12 hrs after in case a short stone top-up is needed.\n" +
                "• GUARDRAIL: Guides only—final readiness is strictly on the user via physical feel and stem bend-and-snap.",
                totalMinutes, totalMinutes / 60.0, totalMinutes / 2);
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
            if (!line.trim().isEmpty() && count < 5) {
                updated.append("\n").append(line);
                count++;
            }
        }
        prefs.edit().putString("calibrated_batches", updated.toString()).apply();
        Toast.makeText(this, "Saved! Engine is continuously calibrating to your press results (Last 5 batches active).", Toast.LENGTH_LONG).show();
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
