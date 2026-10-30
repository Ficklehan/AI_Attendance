package com.attendance.recognition;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.config.DeepSeekProperties;
import com.attendance.config.MimoProperties;
import com.attendance.config.RecognitionProperties;
import com.attendance.dto.NightShiftConfigDTO;
import com.attendance.recognition.dto.BBox;
import com.attendance.recognition.dto.CellCrop;
import com.attendance.recognition.dto.CellEvidence;
import com.attendance.recognition.dto.CellOcrResult;
import com.attendance.recognition.dto.GroundedCell;
import com.attendance.recognition.dto.OcrRawResult;
import com.attendance.recognition.dto.PageLayout;
import com.attendance.recognition.harness.DeepSeekHarnessClient;
import com.attendance.recognition.harness.HarnessHeadlessClient;
import com.attendance.recognition.harness.HarnessRecordPostProcessor;
import com.attendance.recognition.ink.CellInkAnalyzer;
import com.attendance.recognition.ink.LongLineRemover;
import com.attendance.recognition.ink.NameStrikeDetector;
import com.attendance.recognition.ocr.OcrClient;
import com.attendance.service.NightShiftConfigService;
import com.attendance.service.RecognitionEngineConfigService;
import com.attendance.service.RecognitionModelRuntime;
import com.attendance.service.RecognitionTrace;
import com.attendance.util.RecordCountryDefaults;
import com.attendance.util.RecognizedTimeNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ocr_primary：Harness vision_ground + crop_zoom 切格，Java 并行 OCR 印刷列、并行 VLM 读到离格。
 */
@Component
public class StructuredRecognitionPipeline {

    private static final Logger log = LoggerFactory.getLogger(StructuredRecognitionPipeline.class);

    private final RecognitionProperties properties;
    private final DeepSeekHarnessClient harnessClient;
    private final OcrClient ocrClient;
    private final DeepSeekCellAdvisor cellAdvisor;
    private final AttendanceRuleEngine ruleEngine;
    private final NightShiftConfigService nightShiftConfigService;
    private final RecognitionModelRuntime runtime;
    private final DeepSeekProperties deepSeekProperties;
    private final MimoProperties mimoProperties;
    private final HarnessHeadlessClient headlessClient;
    private final HarnessRecordPostProcessor recordPostProcessor;
    private final FlashKeyGate keyGate;
    private final RowGridRebuilder rowGrid;
    private final ExecutorService cellPool;

    public StructuredRecognitionPipeline(RecognitionProperties properties,
                                         DeepSeekHarnessClient harnessClient,
                                         OcrClient ocrClient,
                                         DeepSeekCellAdvisor cellAdvisor,
                                         AttendanceRuleEngine ruleEngine,
                                         NightShiftConfigService nightShiftConfigService,
                                         RecognitionModelRuntime runtime,
                                         DeepSeekProperties deepSeekProperties,
                                         MimoProperties mimoProperties,
                                         HarnessHeadlessClient headlessClient,
                                         HarnessRecordPostProcessor recordPostProcessor,
                                         FlashKeyGate keyGate,
                                         RowGridRebuilder rowGrid) {
        this.properties = properties;
        this.harnessClient = harnessClient;
        this.ocrClient = ocrClient;
        this.cellAdvisor = cellAdvisor;
        this.ruleEngine = ruleEngine;
        this.nightShiftConfigService = nightShiftConfigService;
        this.runtime = runtime;
        this.deepSeekProperties = deepSeekProperties;
        this.mimoProperties = mimoProperties;
        this.headlessClient = headlessClient;
        this.recordPostProcessor = recordPostProcessor;
        this.keyGate = keyGate;
        this.rowGrid = rowGrid;
        final int threads = Math.max(2, properties.getOcr().getParallelism());
        this.cellPool = Executors.newFixedThreadPool(threads, new ThreadFactory() {
            private final AtomicInteger seq = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "harness-cell-" + seq.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        cellPool.shutdownNow();
    }

    public boolean doctorOk() {
        if (!properties.getOcr().isEnabled() || !properties.getHarness().isEnabled()) {
            return false;
        }
        if (!ocrClient.doctorOk()) {
            return false;
        }
        String key = resolveApiKey();
        return key != null && !key.trim().isEmpty();
    }

    public String doctorMessage() {
        if (!properties.getOcr().isEnabled() || !properties.getHarness().isEnabled()) {
            return "ocr/harness disabled";
        }
        if (!ocrClient.doctorOk()) {
            return ocrClient.doctorMessage();
        }
        String key = resolveApiKey();
        if (key == null || key.trim().isEmpty()) {
            return "missing vision API key";
        }
        return "ok";
    }

    public List<JSONObject> recognizePage(byte[] imageBytes, String filename, String promptCountry,
                                          String workingCountry, RecognitionTrace trace) throws Exception {
        Path workDir = Files.createTempDirectory("clockai-harness-");
        try {
            BufferedImage source = DeepSeekHarnessClient.decode(imageBytes);
            if (trace != null) {
                JSONObject start = new JSONObject();
                start.put("filename", filename);
                start.put("pipeline", "ocr_primary");
                start.put("country", promptCountry);
                start.put("delegate", "vision_ground+parallel_vlm");
                start.put("pageW", Integer.valueOf(source.getWidth()));
                start.put("pageH", Integer.valueOf(source.getHeight()));
                start.put("keyPoolSize", Integer.valueOf(runtime.getKeyPoolSize()));
                start.put("model", runtime.getModel());
                trace.step("harness_page_start", start);
            }
            PageLayout layout = harnessClient.groundPage(source, promptCountry, resolveApiKey());
            rowGrid.rebuild(source, layout, workDir);
            if (trace != null) {
                trace.step("layout_detected", layoutTrace(layout));
            }
            if (layout.cells == null || layout.cells.isEmpty()) {
                List<JSONObject> failed = new ArrayList<JSONObject>();
                failed.add(structureFailed(workingCountry, layout, promptCountry));
                return failed;
            }
            List<CellCrop> crops = harnessClient.cropZoom(source, layout, workDir);
            if (trace != null) {
                trace.step("crop_zoom", cropTrace(crops));
            }
            ocrPrintedParallel(crops);
            Map<Integer, Map<String, CellCrop>> byRow = indexCrops(crops);
            List<Integer> rowIds = new ArrayList<Integer>(byRow.keySet());
            Collections.sort(rowIds);
            List<RowWork> works = new ArrayList<RowWork>();
            for (int i = 0; i < rowIds.size(); i++) {
                int row = rowIds.get(i).intValue();
                Map<String, CellCrop> cells = byRow.get(Integer.valueOf(row));
                works.add(new RowWork(row, cells, buildRow(row, cells, layout)));
            }
            fillClocksParallel(works);
            int vlmCells = adviseParallel(works, trace);
            NightShiftConfigDTO nightShift = nightShiftConfigService.getConfigForCountry(promptCountry);
            List<JSONObject> records = new ArrayList<JSONObject>();
            for (int i = 0; i < works.size(); i++) {
                RowWork work = works.get(i);
                JSONObject record = ruleEngine.apply(work.raw, workingCountry, nightShift);
                RecordCountryDefaults.applyWorkingCountryPays(record, workingCountry);
                record.put("_harnessUsed", Boolean.TRUE);
                records.add(record);
                if (trace != null) {
                    JSONObject cellOcr = new JSONObject();
                    cellOcr.put("row", Integer.valueOf(work.row));
                    cellOcr.put("printed", printedTrace(work.cells));
                    cellOcr.put("arrive", evidenceJson(work.raw.arrive));
                    cellOcr.put("depart", evidenceJson(work.raw.depart));
                    trace.step("cell_ocr", cellOcr);
                    JSONObject clock = clockExtractJson(work, record);
                    clock.put("arrivoBbox", record.get("_arrivoBbox"));
                    clock.put("uscitaBbox", record.get("_uscitaBbox"));
                    trace.step("clock_extract", clock);
                }
            }
            dropTrailingEmpty(records);
            if (trace != null) {
                JSONObject done = new JSONObject();
                done.put("vlmCells", Integer.valueOf(vlmCells));
                done.put("recordCount", Integer.valueOf(records.size()));
                done.put("marks", markCounts(records));
                trace.step("harness_page_done", done);
            }
            return records;
        } finally {
            deleteQuietly(workDir);
        }
    }

    private static String pageFileName(String filename) {
        if (filename != null) {
            String lower = filename.toLowerCase(java.util.Locale.ROOT);
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                return "page.jpg";
            }
            if (lower.endsWith(".webp")) {
                return "page.webp";
            }
        }
        return "page.png";
    }

    private OcrRawResult buildRow(int row, Map<String, CellCrop> cells, PageLayout layout) {
        OcrRawResult raw = new OcrRawResult();
        raw.rowIndex = row;
        raw.pageNum = layout.pageNum;
        raw.headerWarehouse = layout.warehouse;
        raw.headerDateRaw = layout.dateRaw;
        raw.noText = ocrText(cells.get("NO"), "NO", row);
        raw.name = ocrText(cells.get("NAME"), "NAME", row);
        if ((raw.noText == null || raw.noText.trim().isEmpty())
                && raw.name != null && !raw.name.trim().isEmpty()) {
            raw.noText = String.valueOf(row);
        }
        raw.agency = ocrText(cells.get("AGENCY"), "AGENCY", row);
        raw.shift = ocrText(cells.get("SHIFT"), "SHIFT", row);
        raw.pauseText = ocrText(cells.get("PAUSE"), "PAUSE", row);
        raw.observations = ocrText(cells.get("OBSERVATIONS"), "OBSERVATIONS", row);
        CellCrop nameCrop = cells.get("NAME");
        if (nameCrop != null && nameCrop.image != null) {
            NameStrikeDetector.StrikeResult strike =
                    NameStrikeDetector.detect(nameCrop.image, properties.getRule());
            raw.nameStruck = strike.struck;
            raw.nameHasReplacement = strike.hasReplacement;
        }
        CellCrop sig = cells.get("SIGNATURE");
        if (sig != null && sig.image != null) {
            raw.signatureHasInk = CellInkAnalyzer.analyze(sig.image, properties.getRule()).hasInk;
            raw.signatureText = raw.signatureHasInk ? "已签字" : "";
        }
        return raw;
    }

    private static boolean hasCrop(CellCrop crop) {
        return crop != null && (crop.image != null || crop.pngPath != null);
    }

    private static void markMissingClock(CellEvidence evidence) {
        evidence.hasInk = true;
        evidence.inkAfterLineRemoval = true;
        evidence.lowConfidence = true;
        evidence.source = "ground_missing";
        evidence.putDiag("clockPick", "ground_missing");
        evidence.putDiag("advise", "need_ground_missing");
    }

    private void fillClock(CellEvidence evidence, CellCrop crop) throws Exception {
        if (crop == null || crop.image == null) {
            evidence.putDiag("clockPick", "no_crop");
            return;
        }
        evidence.bbox = crop.bbox;
        evidence.cropPath = crop.pngPath != null ? crop.pngPath.toString() : "";
        evidence.putDiag("cropW", Integer.valueOf(crop.image.getWidth()));
        evidence.putDiag("cropH", Integer.valueOf(crop.image.getHeight()));
        RecognitionProperties.Rule rule = properties.getRule();
        CellInkAnalyzer.InkStats ink = CellInkAnalyzer.analyzeClock(crop.image, rule);
        evidence.hasInk = ink.hasInk;
        evidence.putDiag("inkDarkRatio", round4(ink.darkRatio));
        evidence.putDiag("inkVariance", round4(ink.variance));
        evidence.putDiag("inkHas", Boolean.valueOf(ink.hasInk));
        if (!ink.hasInk) {
            evidence.inkAfterLineRemoval = false;
            evidence.text = "";
            evidence.source = "blank";
            evidence.putDiag("clockPick", "blank_no_ink");
            return;
        }
        BufferedImage forOcr = crop.image;
        if (rule.isIgnoreLongLine()) {
            LongLineRemover.LineResult line = LongLineRemover.remove(crop.image, rule);
            evidence.hasLongLine = line.hasLongLine;
            forOcr = line.cleaned != null ? line.cleaned : crop.image;
        }
        evidence.putDiag("longLine", Boolean.valueOf(evidence.hasLongLine));
        CellInkAnalyzer.InkStats after = CellInkAnalyzer.analyzeClock(forOcr, rule);
        evidence.inkAfterLineRemoval = after.hasInk;
        evidence.putDiag("inkDarkRatioAfterLine", round4(after.darkRatio));
        evidence.putDiag("inkVarianceAfterLine", round4(after.variance));
        evidence.putDiag("inkAfterLine", Boolean.valueOf(after.hasInk));
        if (!evidence.inkAfterLineRemoval) {
            evidence.hasInk = false;
            evidence.text = "";
            evidence.source = "blank";
            evidence.putDiag("clockPick", "blank_after_line");
            return;
        }
        evidence.source = "crop";
        evidence.text = "";
        evidence.lowConfidence = true;
        evidence.putDiag("clockPick", "ink_need_vlm");
        evidence.putDiag("ocrTextBeforeFlash", "");
    }

    private String readDigitBoxes(BufferedImage image, Path original, CellEvidence evidence,
                                  RecognitionProperties.Rule rule) {
        ClockDigitGrid.Layout layout = ClockDigitGrid.locate(image,
                rule != null ? rule.getDarkLuminance() : 165);
        evidence.putDiag("digitBoxSrc", layout.source);
        if (layout.boxes == null || layout.boxes.length != 4) {
            return "";
        }
        Character[] digits = new Character[4];
        int inkBoxes = 0;
        int readBoxes = 0;
        StringBuilder raws = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (i > 0) {
                raws.append('|');
            }
            BufferedImage piece = ClockDigitGrid.crop(image, layout.boxes[i]);
            if (piece == null) {
                raws.append('-');
                continue;
            }
            CellInkAnalyzer.InkStats boxInk = CellInkAnalyzer.analyzeGlyph(piece, rule);
            evidence.putDiag("digitInk" + i, Boolean.valueOf(boxInk.hasInk));
            evidence.putDiag("digitDark" + i, round4(boxInk.darkRatio));
            if (!boxInk.hasInk) {
                raws.append('_');
                continue;
            }
            inkBoxes++;
            try {
                CellOcrResult ocr = ocrClient.ocrCell(writeTemp(piece, sibling(original, "-d" + i)));
                String raw = ocr.primaryDigitLine();
                if (raw == null || raw.trim().isEmpty()) {
                    raw = ocr.primaryLine();
                }
                raws.append(raw == null ? "" : raw.replace('|', '/'));
                Character d = ClockDigitGrid.parseDigit(raw);
                digits[i] = d;
                if (d != null) {
                    readBoxes++;
                }
            } catch (Exception e) {
                raws.append('?');
                evidence.putDiag("digitError" + i, e.getMessage());
            }
        }
        evidence.putDiag("digitRaws", raws.toString());
        evidence.putDiag("digitChars", ClockDigitGrid.formatDigits(digits));
        evidence.putDiag("digitInkBoxes", Integer.valueOf(inkBoxes));
        evidence.putDiag("digitRead", Integer.valueOf(readBoxes));
        if (inkBoxes == 0) {
            return "";
        }
        String clock = ClockDigitGrid.compose(digits);
        evidence.putDiag("digitClock", clock);
        return clock;
    }

    private String splitClockOcr(BufferedImage image, Path original, CellEvidence evidence) {
        if (image == null) {
            return "";
        }
        int w = image.getWidth();
        int h = image.getHeight();
        if (w < 16 || h < 8) {
            return "";
        }
        int gap = Math.max(2, w / 16);
        int mid = w / 2;
        try {
            BufferedImage left = image.getSubimage(0, 0, Math.max(1, mid - gap), h);
            BufferedImage right = image.getSubimage(Math.min(w - 1, mid + gap), 0,
                    Math.max(1, w - mid - gap), h);
            CellOcrResult lh = ocrClient.ocrCell(writeTemp(left, sibling(original, "-L")));
            CellOcrResult rh = ocrClient.ocrCell(writeTemp(right, sibling(original, "-R")));
            String hoursRaw = lh.primaryDigitLine();
            String minutesRaw = rh.primaryDigitLine();
            String hours = digitsOnly(hoursRaw);
            String minutes = digitsOnly(minutesRaw);
            evidence.putDiag("splitLeftRaw", hoursRaw);
            evidence.putDiag("splitRightRaw", minutesRaw);
            evidence.putDiag("splitLeft", hours);
            evidence.putDiag("splitRight", minutes);
            evidence.putDiag("splitLeftConf", round4(lh.confidence));
            evidence.putDiag("splitRightConf", round4(rh.confidence));
            if (hours.length() >= 1 && hours.length() <= 2
                    && minutes.length() >= 1 && minutes.length() <= 2) {
                String joined = RecognizedTimeNormalizer.normalizeClockTime(hours + ":" + minutes);
                String clock = joined != null && joined.matches("\\d{2}:\\d{2}") ? joined : "";
                evidence.putDiag("splitClock", clock);
                evidence.putDiag("splitJoin", hours + ":" + minutes);
                return clock;
            }
            evidence.putDiag("splitClock", "");
        } catch (Exception e) {
            log.debug("split clock ocr failed: {}", e.getMessage());
            evidence.putDiag("splitError", e.getMessage());
        }
        return "";
    }

    private void ocrPrintedParallel(List<CellCrop> crops) throws Exception {
        List<Callable<Void>> tasks = new ArrayList<Callable<Void>>();
        for (int i = 0; i < crops.size(); i++) {
            final CellCrop crop = crops.get(i);
            if (crop == null || crop.pngPath == null || skipPrintedOcr(crop.field)) {
                continue;
            }
            tasks.add(new Callable<Void>() {
                @Override
                public Void call() {
                    try {
                        crop.ocr = ocrClient.ocrCell(crop.pngPath);
                    } catch (Exception e) {
                        log.warn("ocr printed row={} field={}: {}", crop.row, crop.field, e.getMessage());
                        crop.ocr = CellOcrResult.empty();
                        crop.ocr.lowConfidence = true;
                    }
                    return null;
                }
            });
        }
        runAll(tasks, properties.getOcr().getTimeoutMs());
    }

    private void fillClocksParallel(List<RowWork> works) throws Exception {
        List<Callable<Void>> tasks = new ArrayList<Callable<Void>>();
        for (int i = 0; i < works.size(); i++) {
            final RowWork work = works.get(i);
            tasks.add(new Callable<Void>() {
                @Override
                public Void call() {
                    try {
                        if (hasCrop(work.cells.get("ARRIVEE"))) {
                            fillClock(work.raw.arrive, work.cells.get("ARRIVEE"));
                        } else {
                            markMissingClock(work.raw.arrive);
                        }
                        if (hasCrop(work.cells.get("DEPAR"))) {
                            fillClock(work.raw.depart, work.cells.get("DEPAR"));
                        } else {
                            markMissingClock(work.raw.depart);
                        }
                    } catch (Exception e) {
                        log.warn("clock ocr row={}: {}", work.row, e.getMessage());
                        work.raw.arrive.lowConfidence = true;
                        work.raw.depart.lowConfidence = true;
                    }
                    return null;
                }
            });
        }
        runAll(tasks, properties.getOcr().getTimeoutMs() * 6);
    }

    private int adviseParallel(List<RowWork> works, RecognitionTrace trace) throws Exception {
        double review = properties.getRule().getReviewThreshold();
        List<AdviseJob> jobs = new ArrayList<AdviseJob>();
        JSONArray skip = new JSONArray();
        for (int i = 0; i < works.size(); i++) {
            RowWork work = works.get(i);
            planAdvise(work, "ARRIVEE", work.raw.arrive, work.cells.get("ARRIVEE"), review, jobs, skip);
            planAdvise(work, "DEPAR", work.raw.depart, work.cells.get("DEPAR"), review, jobs, skip);
            planNameAdvise(work, jobs, skip);
        }
        int wanted = jobs.size();
        int cap = properties.getHarness().getMaxVlmCellsPerPage();
        JSONArray capped = new JSONArray();
        if (jobs.size() > cap) {
            for (int i = cap; i < jobs.size(); i++) {
                AdviseJob extra = jobs.get(i);
                if (extra.evidence != null) {
                    extra.evidence.putDiag("flashSkip", "vlm_cap");
                    extra.evidence.putDiag("advise", "need_but_vlm_cap");
                }
                JSONObject s = new JSONObject();
                s.put("row", extra.row);
                s.put("field", extra.field);
                s.put("reason", "need_but_vlm_cap");
                capped.add(s);
            }
            jobs = new ArrayList<AdviseJob>(jobs.subList(0, cap));
        }
        if (trace != null) {
            JSONObject plan = new JSONObject();
            plan.put("wanted", wanted);
            plan.put("cap", cap);
            plan.put("queued", jobs.size());
            plan.put("skip", skip);
            plan.put("capped", capped);
            trace.step("flash_plan", plan);
        }
        int ran = runAdviseJobs(jobs, trace, false);
        List<AdviseJob> retries = new ArrayList<AdviseJob>();
        for (int i = 0; i < jobs.size(); i++) {
            AdviseJob job = jobs.get(i);
            if (job.evidence != null && shouldRetryIllegible(job.evidence)
                    && job.crop != null && job.crop.image != null) {
                BufferedImage inset = insetImage(job.crop.image, 0.12d);
                job.evidence.putDiag("flashRetry", Boolean.TRUE);
                retries.add(new AdviseJob(job.evidence, job.crop, job.row, job.field, job.work, inset));
            }
        }
        if (!retries.isEmpty()) {
            ran += runAdviseJobs(retries, trace, true);
        }
        return ran;
    }

    private int runAdviseJobs(List<AdviseJob> jobs, RecognitionTrace trace, boolean retry) throws Exception {
        if (jobs.isEmpty()) {
            return 0;
        }
        List<Callable<Void>> tasks = new ArrayList<Callable<Void>>();
        for (int i = 0; i < jobs.size(); i++) {
            final AdviseJob job = jobs.get(i);
            tasks.add(new Callable<Void>() {
                @Override
                public Void call() {
                    FlashKeyGate.HeldKey held = keyGate.acquireOrFallback(resolveApiKey());
                    try {
                        BufferedImage img = job.image != null ? job.image : job.crop.image;
                        if ("NAME".equals(job.field)) {
                            String before = job.work.raw.name;
                            String name = cellAdvisor.adviseName(img, held.apiKey());
                            if (name != null && !name.trim().isEmpty()) {
                                job.work.raw.name = name.trim();
                            }
                            if (trace != null) {
                                JSONObject d = new JSONObject();
                                d.put("row", job.row);
                                d.put("field", "NAME");
                                d.put("ocrBefore", before);
                                d.put("name", job.work.raw.name);
                                d.put("keyIndex", Integer.valueOf(held.keyIndex()));
                                d.put("retry", Boolean.valueOf(retry));
                                trace.step("harness_cell", d);
                            }
                            return null;
                        }
                        Object before = job.evidence.diag.get("ocrTextBeforeFlash");
                        cellAdvisor.advise(job.evidence, img, held.apiKey());
                        job.evidence.putDiag("keyIndex", Integer.valueOf(held.keyIndex()));
                        if (trace != null) {
                            JSONObject d = new JSONObject();
                            d.put("row", job.row);
                            d.put("field", job.field);
                            d.put("ocrBefore", before == null ? "" : String.valueOf(before));
                            d.put("advise", job.evidence.diag.get("advise"));
                            d.put("time", job.evidence.text);
                            d.put("lowConfidence", job.evidence.lowConfidence);
                            d.put("flashTimeRaw", job.evidence.diag.get("flashTimeRaw"));
                            d.put("flashIllegible", job.evidence.diag.get("flashIllegible"));
                            d.put("flashRaw", job.evidence.diag.get("flashRaw"));
                            d.put("source", job.evidence.source);
                            d.put("keyIndex", Integer.valueOf(held.keyIndex()));
                            d.put("retry", Boolean.valueOf(retry));
                            trace.step("harness_cell", d);
                        }
                    } catch (Exception e) {
                        log.warn("cell advisor failed row={} field={}: {}", job.row, job.field, e.getMessage());
                        if (job.evidence != null) {
                            job.evidence.lowConfidence = true;
                            job.evidence.putDiag("flashError", e.getMessage());
                        }
                        if (trace != null) {
                            JSONObject d = new JSONObject();
                            d.put("row", job.row);
                            d.put("field", job.field);
                            d.put("error", e.getMessage());
                            d.put("keyIndex", Integer.valueOf(held.keyIndex()));
                            trace.step("harness_cell", d);
                        }
                    } finally {
                        held.close();
                    }
                    return null;
                }
            });
        }
        int vlmP = Math.max(1, Math.min(properties.getHarness().getVlmParallelism(),
                Math.max(1, runtime.getKeyPoolSize())));
        for (int from = 0; from < tasks.size(); from += vlmP) {
            int to = Math.min(from + vlmP, tasks.size());
            runAll(new ArrayList<Callable<Void>>(tasks.subList(from, to)),
                    properties.getHarness().getVlmTimeoutMs());
        }
        return jobs.size();
    }

    private void planAdvise(RowWork work, String field, CellEvidence evidence, CellCrop crop,
                            double review, List<AdviseJob> jobs, JSONArray skip) {
        String decision = adviseDecision(evidence, review);
        evidence.putDiag("advise", decision);
        if (!decision.startsWith("need_")) {
            evidence.putDiag("flashSkip", decision);
            JSONObject s = new JSONObject();
            s.put("row", work.row);
            s.put("field", field);
            s.put("reason", decision);
            s.put("text", evidence.text);
            s.put("conf", round4(evidence.confidence));
            skip.add(s);
            return;
        }
        if (!hasCrop(crop)) {
            evidence.putDiag("flashSkip", "no_crop");
            JSONObject s = new JSONObject();
            s.put("row", work.row);
            s.put("field", field);
            s.put("reason", "no_crop");
            skip.add(s);
            return;
        }
        jobs.add(new AdviseJob(evidence, crop, work.row, field, work, crop.image));
    }

    private void planNameAdvise(RowWork work, List<AdviseJob> jobs, JSONArray skip) {
        if (!OcrLinePicker.needsNameVlm(work.raw.name)) {
            return;
        }
        CellCrop crop = work.cells.get("NAME");
        if (!hasCrop(crop) || crop.image == null) {
            JSONObject s = new JSONObject();
            s.put("row", work.row);
            s.put("field", "NAME");
            s.put("reason", "no_crop");
            skip.add(s);
            return;
        }
        jobs.add(new AdviseJob(null, crop, work.row, "NAME", work, crop.image));
    }

    static boolean shouldRetryIllegible(CellEvidence evidence) {
        if (evidence == null) {
            return false;
        }
        if (evidence.hasLegalClock()) {
            return false;
        }
        Object illegible = evidence.diag.get("flashIllegible");
        if (!Boolean.TRUE.equals(illegible)) {
            return false;
        }
        return evidence.hasInk || evidence.inkAfterLineRemoval || evidence.hasLongLine;
    }

    static String adviseDecision(CellEvidence evidence, double reviewThreshold) {
        if (evidence == null) {
            return "skip_null";
        }
        boolean ink = evidence.inkAfterLineRemoval || evidence.hasInk;
        if (!ink) {
            if (evidence.hasLongLine) {
                return "need_borderline_line";
            }
            double ratio = toDouble(evidence.diag.get("inkDarkRatio"));
            if (ratio >= 0.015d) {
                return "need_borderline_ink";
            }
            return "skip_no_ink";
        }
        boolean legal = evidence.hasLegalClock();
        boolean confOk = evidence.confidence < 0 || evidence.confidence >= reviewThreshold;
        if (legal && !evidence.lowConfidence && confOk) {
            return "skip_ocr_legal";
        }
        if (!legal) {
            return "need_no_legal";
        }
        if (evidence.lowConfidence) {
            return "need_low_conf";
        }
        return "need_conf_below_review";
    }

    static double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    static BufferedImage insetImage(BufferedImage src, double frac) {
        if (src == null) {
            return src;
        }
        int padX = Math.max(2, (int) Math.round(src.getWidth() * frac));
        int padY = Math.max(2, (int) Math.round(src.getHeight() * frac));
        int w = src.getWidth() - 2 * padX;
        int h = src.getHeight() - 2 * padY;
        if (w < 8 || h < 8) {
            return src;
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, w, h, padX, padY, padX + w, padY + h, null);
        g.dispose();
        return out;
    }

    private void runAll(List<Callable<Void>> tasks, int perTaskMs) throws Exception {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        int workers = Math.max(1, properties.getOcr().getParallelism());
        long timeoutSec = Math.max(30L, ((tasks.size() + workers - 1) / workers)
                * Math.max(1L, perTaskMs / 1000L) + 10L);
        cellPool.invokeAll(tasks, timeoutSec, TimeUnit.SECONDS);
    }

    private String ocrText(CellCrop crop, String key, int row) {
        if (crop == null) {
            return "";
        }
        CellOcrResult ocr = crop.ocr;
        if (ocr == null) {
            return "";
        }
        if ("NO".equals(key)) {
            return OcrLinePicker.number(ocr, row);
        }
        if ("PAUSE".equals(key)) {
            String n = OcrLinePicker.number(ocr);
            return n.isEmpty() ? ocr.primaryDigitLine() : n;
        }
        if ("SHIFT".equals(key)) {
            return OcrLinePicker.shift(ocr);
        }
        if ("NAME".equals(key)) {
            return OcrLinePicker.name(ocr);
        }
        if ("AGENCY".equals(key)) {
            return OcrLinePicker.agency(ocr);
        }
        return ocr.primaryLine();
    }

    static void dropTrailingEmpty(List<JSONObject> records) {
        if (records == null) {
            return;
        }
        while (!records.isEmpty()) {
            JSONObject last = records.get(records.size() - 1);
            if (!isBlankRecord(last)) {
                return;
            }
            records.remove(records.size() - 1);
        }
    }

    private static boolean isBlankRecord(JSONObject rec) {
        if (rec == null) {
            return true;
        }
        return blank(rec.getString("NOM_PRENOM"))
                && blank(rec.getString("ARRIVEE"))
                && blank(rec.getString("DEPAR"))
                && blank(rec.getString("AGENCE_INTERIMAIRE"));
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static JSONObject printedTrace(Map<String, CellCrop> cells) {
        JSONObject printed = new JSONObject();
        if (cells == null) {
            return printed;
        }
        String[] keys = new String[] {"NO", "NAME", "AGENCY", "SHIFT", "PAUSE", "OBSERVATIONS"};
        for (int i = 0; i < keys.length; i++) {
            CellCrop crop = cells.get(keys[i]);
            if (crop == null) {
                continue;
            }
            JSONObject cell = new JSONObject();
            CellOcrResult ocr = crop.ocr;
            cell.put("text", ocr == null ? "" : ocr.primaryLine());
            cell.put("lines", ocr == null ? Collections.emptyList() : ocr.lines);
            cell.put("conf", ocr == null ? 0 : ocr.confidence);
            cell.put("low", ocr != null && ocr.lowConfidence);
            cell.put("crop", crop.pngPath != null ? crop.pngPath.toString() : "");
            putBBox(cell, crop.bbox);
            printed.put(keys[i], cell);
        }
        return printed;
    }

    private static boolean skipPrintedOcr(String field) {
        return "ARRIVEE".equals(field) || "DEPAR".equals(field) || "SIGNATURE".equals(field);
    }

    private static Path sibling(Path original, String suffix) {
        if (original == null) {
            return null;
        }
        String name = original.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        return original.resolveSibling(stem + suffix + ".png");
    }

    private static String digitsOnly(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isColonGlyph(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim();
        return ":".equals(t) || "：".equals(t) || ".".equals(t) || "·".equals(t);
    }

    private static final class RowWork {
        final int row;
        final Map<String, CellCrop> cells;
        final OcrRawResult raw;

        RowWork(int row, Map<String, CellCrop> cells, OcrRawResult raw) {
            this.row = row;
            this.cells = cells;
            this.raw = raw;
        }
    }

    private static final class AdviseJob {
        final CellEvidence evidence;
        final CellCrop crop;
        final int row;
        final String field;
        final RowWork work;
        final BufferedImage image;

        AdviseJob(CellEvidence evidence, CellCrop crop, int row, String field, RowWork work, BufferedImage image) {
            this.evidence = evidence;
            this.crop = crop;
            this.row = row;
            this.field = field;
            this.work = work;
            this.image = image;
        }
    }

    private Path writeTemp(BufferedImage image, Path original) throws Exception {
        Path out;
        if (original == null) {
            out = Files.createTempFile("clock-cell-", ".png");
        } else {
            String name = original.getFileName().toString();
            if (name.endsWith(".png")) {
                name = name.substring(0, name.length() - 4) + "-clean.png";
            } else {
                name = name + "-clean.png";
            }
            out = original.resolveSibling(name);
        }
        javax.imageio.ImageIO.write(image, "png", out.toFile());
        return out;
    }

    private Map<Integer, Map<String, CellCrop>> indexCrops(List<CellCrop> crops) {
        Map<Integer, Map<String, CellCrop>> map = new HashMap<Integer, Map<String, CellCrop>>();
        for (CellCrop crop : crops) {
            Map<String, CellCrop> row = map.get(Integer.valueOf(crop.row));
            if (row == null) {
                row = new HashMap<String, CellCrop>();
                map.put(Integer.valueOf(crop.row), row);
            }
            row.put(crop.field, crop);
        }
        return map;
    }

    private String resolveApiKey() {
        List<String> keys;
        if (RecognitionEngineConfigService.ENGINE_DEEPSEEK.equals(runtime.getActiveEngine())) {
            keys = deepSeekProperties.getResolvedApiKeys();
            if (keys.isEmpty() && deepSeekProperties.getApiKey() != null) {
                return deepSeekProperties.getApiKey().trim();
            }
        } else {
            keys = mimoProperties.getResolvedApiKeys();
            if (keys.isEmpty() && mimoProperties.getApiKey() != null) {
                return mimoProperties.getApiKey().trim();
            }
        }
        return keys.isEmpty() ? "" : keys.get(0);
    }

    static JSONObject structureFailed(AttendanceRuleEngine engine, String workingCountry,
                                      PageLayout layout, NightShiftConfigDTO nightShift) {
        OcrRawResult raw = new OcrRawResult();
        raw.noText = "1";
        raw.arrive.hasInk = true;
        raw.arrive.inkAfterLineRemoval = true;
        raw.arrive.lowConfidence = true;
        raw.depart.hasInk = true;
        raw.depart.inkAfterLineRemoval = true;
        raw.depart.lowConfidence = true;
        if (layout != null) {
            raw.headerWarehouse = layout.warehouse;
            raw.headerDateRaw = layout.dateRaw;
            raw.pageNum = layout.pageNum;
        }
        JSONObject rec = engine.apply(raw, workingCountry, nightShift);
        rec.put("_structureFailed", Boolean.TRUE);
        rec.put("_reviewReason", "ground_failed");
        return rec;
    }

    private JSONObject structureFailed(String workingCountry, PageLayout layout, String promptCountry) {
        return structureFailed(ruleEngine, workingCountry, layout,
                nightShiftConfigService.getConfigForCountry(promptCountry));
    }

    private static JSONObject layoutTrace(PageLayout layout) {
        JSONObject g = new JSONObject();
        g.put("cellCount", layout.cells.size());
        g.put("source", layout.source);
        g.put("warehouse", layout.warehouse);
        g.put("dateRaw", layout.dateRaw);
        g.put("pageNum", layout.pageNum);
        g.put("gridXs", intArray(layout.gridXs));
        g.put("gridYs", intArray(layout.gridYs));
        g.put("headerRows", layout.headerRows);
        g.put("pageW", layout.sourceWidth);
        g.put("pageH", layout.sourceHeight);
        g.put("gridSource", layout.gridSource);
        g.put("rowH", Integer.valueOf(layout.rowH));
        g.put("firstY", Integer.valueOf(layout.firstY));
        g.put("bandCount", Integer.valueOf(layout.bandCount));
        int rows = 0;
        if (layout.gridYs != null && layout.gridYs.length > 0) {
            rows = layout.gridYs.length;
        } else {
            for (int i = 0; i < layout.cells.size(); i++) {
                rows = Math.max(rows, layout.cells.get(i).row);
            }
        }
        g.put("rowCount", Integer.valueOf(rows));
        JSONArray clockBoxes = new JSONArray();
        JSONArray cells = new JSONArray();
        int cap = Math.min(layout.cells.size(), 80);
        for (int i = 0; i < layout.cells.size(); i++) {
            GroundedCell cell = layout.cells.get(i);
            if ("ARRIVEE".equals(cell.field) || "DEPAR".equals(cell.field)) {
                JSONObject c = new JSONObject();
                c.put("row", cell.row);
                c.put("field", cell.field);
                putBBox(c, cell.bbox);
                clockBoxes.add(c);
            }
            if (i < cap) {
                JSONObject c = new JSONObject();
                c.put("row", cell.row);
                c.put("field", cell.field);
                putBBox(c, cell.bbox);
                cells.add(c);
            }
        }
        g.put("clockBoxes", clockBoxes);
        g.put("cells", cells);
        g.put("cellsTruncated", layout.cells.size() > 80);
        return g;
    }

    private static JSONObject cropTrace(List<CellCrop> crops) {
        JSONObject cz = new JSONObject();
        cz.put("count", crops.size());
        JSONArray files = new JSONArray();
        JSONArray clockFiles = new JSONArray();
        int cap = Math.min(crops.size(), 80);
        for (int i = 0; i < crops.size(); i++) {
            CellCrop crop = crops.get(i);
            JSONObject f = cropFileJson(crop);
            if ("ARRIVEE".equals(crop.field) || "DEPAR".equals(crop.field)) {
                clockFiles.add(f);
            }
            if (i < cap) {
                files.add(f);
            }
        }
        cz.put("clockFiles", clockFiles);
        cz.put("files", files);
        cz.put("filesTruncated", crops.size() > 80);
        return cz;
    }

    private static JSONObject cropFileJson(CellCrop crop) {
        JSONObject f = new JSONObject();
        f.put("row", crop.row);
        f.put("field", crop.field);
        f.put("path", crop.pngPath != null ? crop.pngPath.toString() : "");
        if (crop.image != null) {
            f.put("w", crop.image.getWidth());
            f.put("h", crop.image.getHeight());
        }
        putBBox(f, crop.bbox);
        return f;
    }

    private static JSONObject evidenceJson(CellEvidence evidence) {
        JSONObject o = new JSONObject();
        putBBox(o, evidence.bbox);
        o.put("crop", evidence.cropPath);
        o.put("ink", evidence.hasInk);
        o.put("inkAfterLine", evidence.inkAfterLineRemoval);
        o.put("longLine", evidence.hasLongLine);
        o.put("text", evidence.text);
        o.put("conf", evidence.confidence);
        o.put("low", evidence.lowConfidence);
        o.put("lines", evidence.ocrLines);
        o.put("source", evidence.source);
        o.put("diag", new JSONObject(evidence.diag));
        return o;
    }

    private static JSONObject clockExtractJson(RowWork work, JSONObject record) {
        JSONObject row = new JSONObject();
        row.put("row", work.row);
        row.put("no", work.raw.noText);
        row.put("name", work.raw.name);
        row.put("shift", work.raw.shift);
        row.put("arrive", clockCellJson(work.raw.arrive, record.getString("ARRIVEE")));
        row.put("depart", clockCellJson(work.raw.depart, record.getString("DEPAR")));
        row.put("Mark", record.getString("Mark"));
        row.put("markWhy", record.getString("_markWhy"));
        return row;
    }

    private static JSONObject clockCellJson(CellEvidence ev, String written) {
        JSONObject o = new JSONObject();
        o.put("written", written);
        o.put("text", ev.text);
        o.put("src", ev.source);
        o.put("ink", ev.hasInk);
        o.put("longLine", ev.hasLongLine);
        o.put("conf", round4(ev.confidence));
        o.put("ocrPrimary", ev.diag.get("ocrPrimary"));
        o.put("pickerClock", ev.diag.get("pickerClock"));
        o.put("digitChars", ev.diag.get("digitChars"));
        o.put("digitRaws", ev.diag.get("digitRaws"));
        o.put("digitClock", ev.diag.get("digitClock"));
        o.put("digitBoxSrc", ev.diag.get("digitBoxSrc"));
        o.put("clockPick", ev.diag.get("clockPick"));
        o.put("advise", ev.diag.get("advise"));
        o.put("flashSkip", ev.diag.get("flashSkip"));
        o.put("flashTime", ev.diag.get("flashTime"));
        o.put("flashIllegible", ev.diag.get("flashIllegible"));
        o.put("inkDarkRatio", ev.diag.get("inkDarkRatio"));
        o.put("inkDarkRatioAfterLine", ev.diag.get("inkDarkRatioAfterLine"));
        return o;
    }

    private static JSONObject markCounts(List<JSONObject> records) {
        int normal = 0;
        int blur = 0;
        int absent = 0;
        int other = 0;
        for (int i = 0; i < records.size(); i++) {
            String mark = records.get(i).getString("Mark");
            if (mark == null) {
                other++;
            } else if (mark.contains("模糊")) {
                blur++;
            } else if (mark.contains("未出勤")) {
                absent++;
            } else if (mark.contains("正常")) {
                normal++;
            } else {
                other++;
            }
        }
        JSONObject c = new JSONObject();
        c.put("正常", normal);
        c.put("模糊", blur);
        c.put("未出勤", absent);
        c.put("other", other);
        return c;
    }

    private static JSONArray intArray(int[] values) {
        JSONArray arr = new JSONArray();
        if (values == null) {
            return arr;
        }
        for (int i = 0; i < values.length; i++) {
            arr.add(Integer.valueOf(values[i]));
        }
        return arr;
    }

    private static double round4(double v) {
        return Math.round(v * 10000d) / 10000d;
    }

    private static void putBBox(JSONObject o, BBox box) {
        if (o == null || box == null) {
            return;
        }
        o.put("x", box.x);
        o.put("y", box.y);
        o.put("w", box.w);
        o.put("h", box.h);
    }

    private static boolean isSlashGlyph(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim();
        return "/".equals(t) || "\\".equals(t) || "1".equals(t) || "l".equals(t) || "I".equals(t) || "|".equals(t);
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try {
            File[] files = dir.toFile().listFiles();
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    files[i].delete();
                }
            }
            dir.toFile().delete();
        } catch (Exception ignored) {
            // temp cleanup
        }
    }
}
