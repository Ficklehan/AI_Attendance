package com.attendance.recognition;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.config.DeepSeekProperties;
import com.attendance.config.RecognitionProperties;
import com.attendance.dto.NightShiftConfigDTO;
import com.attendance.recognition.dto.BBox;
import com.attendance.recognition.dto.CellEvidence;
import com.attendance.recognition.dto.CellOcrResult;
import com.attendance.recognition.dto.OcrRawResult;
import com.attendance.recognition.dto.PageLayout;
import com.attendance.recognition.harness.VisionClient;
import com.attendance.recognition.ocr.OcrClient;
import com.attendance.service.NightShiftConfigService;
import com.attendance.service.RecognitionModelRuntime;
import com.attendance.service.RecognitionTrace;
import com.attendance.util.RecordCountryDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 混合识别：视觉模型粗定位列 + 投影法精修时钟列 + 本地 ONNX OCR 读数字 + 视觉读姓名/页头。
 */
@Component
public class StructuredRecognitionPipeline {

    private static final Logger log = LoggerFactory.getLogger(StructuredRecognitionPipeline.class);

    private final RecognitionProperties properties;
    private final OcrClient ocrClient;
    private final AttendanceRuleEngine ruleEngine;
    private final NightShiftConfigService nightShiftConfigService;
    private final RecognitionModelRuntime runtime;
    private final DeepSeekProperties deepSeekProperties;
    private final VisionClient visionClient;

    public StructuredRecognitionPipeline(RecognitionProperties properties,
                                         OcrClient ocrClient,
                                         AttendanceRuleEngine ruleEngine,
                                         NightShiftConfigService nightShiftConfigService,
                                         RecognitionModelRuntime runtime,
                                         DeepSeekProperties deepSeekProperties,
                                         VisionClient visionClient) {
        this.properties = properties;
        this.ocrClient = ocrClient;
        this.ruleEngine = ruleEngine;
        this.nightShiftConfigService = nightShiftConfigService;
        this.runtime = runtime;
        this.deepSeekProperties = deepSeekProperties;
        this.visionClient = visionClient;
    }

    public boolean doctorOk() {
        if (!properties.getOcr().isEnabled()) {
            return false;
        }
        if (!ocrClient.doctorOk()) {
            return false;
        }
        String key = resolveApiKey();
        return key != null && !key.trim().isEmpty();
    }

    public String doctorMessage() {
        if (!properties.getOcr().isEnabled()) {
            return "ocr disabled";
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
        Path workDir = RecognitionDebugDumper.createWorkDir(properties, trace, filename);
        long pageStart = System.currentTimeMillis();
        try {
            Path pageFile = writeOriginal(workDir, imageBytes, filename);
            BufferedImage source = tryDecode(imageBytes);
            if (source == null) {
                throw new IllegalStateException("无法解码考勤原图");
            }
            int W = source.getWidth();
            int H = source.getHeight();

            if (trace != null) {
                JSONObject start = new JSONObject();
                start.put("filename", filename);
                start.put("pipeline", "ocr_primary");
                start.put("delegate", "hybrid_java_ocr");
                start.put("country", promptCountry);
                start.put("pageFile", pageFile.toAbsolutePath().toString());
                start.put("pageW", Integer.valueOf(W));
                start.put("pageH", Integer.valueOf(H));
                start.put("model", runtime.getModel());
                start.put("debug", Boolean.valueOf(properties.isDebug()));
                start.put("workDir", workDir.toAbsolutePath().toString());
                trace.step("harness_page_start", start);
            }

            // 1. 表体左右边界（投影法竖边 min/max），失败回退 detectLayout。
            int[] bounds = tableBounds(source);
            if (bounds == null) {
                return structureFailedPage(workDir, trace, workingCountry, promptCountry, pageStart, "");
            }
            int tableX0 = bounds[0];
            int tableX1 = bounds[1];

            // 2. 行检测（表体全宽范围，行横线贯穿更明显）。
            int[] rowEdges = TableGridDetector.detectRowEdges(source, tableX0 + 4, tableX1 - 4);
            if (rowEdges == null || rowEdges.length < 3) {
                return structureFailedPage(workDir, trace, workingCountry, promptCountry, pageStart, "");
            }

            // 3. 页头（仓库/日期）。
            String[] header = readHeader(source, workDir);

            // 4. 整表读一次读全所有列；失败回退逐列识别。
            int headerRows = 1;
            double accept = properties.getRule().getAcceptThreshold();
            NightShiftConfigDTO nightShift = nightShiftConfigService.getConfigForCountry(promptCountry);
            JSONArray tableRows = readTableBody(source, tableX0, tableX1, rowEdges, headerRows);
            TableGridDetector.Layout layout = null;
            JSONArray names = null;
            List<String> arriveTimes = null;
            List<String> departTimes = null;
            if (tableRows == null) {
                layout = resolveColumns(source, promptCountry, trace);
                if (layout != null) {
                    names = readNames(source, layout.nameX0, layout.nameX1, rowEdges, workDir);
                    arriveTimes = readClockColumnVisual(source, layout.arriveX0, layout.arriveX1, rowEdges, headerRows);
                    departTimes = readClockColumnVisual(source, layout.departX0, layout.departX1, rowEdges, headerRows);
                }
            }

            List<JSONObject> records = new ArrayList<JSONObject>();
            int rowCount = Math.max(0, rowEdges.length - 1 - headerRows);
            for (int r = 0; r < rowCount; r++) {
                int y = rowEdges[headerRows + r];
                int h = rowEdges[headerRows + r + 1] - y;
                if (h < 8) {
                    continue;
                }
                OcrRawResult raw = new OcrRawResult();
                raw.rowIndex = r + 1;
                JSONObject row = (tableRows != null && r < tableRows.size()) ? tableRows.getJSONObject(r) : null;
                if (row != null) {
                    fillFromTableRow(raw, row);
                } else if (layout != null) {
                    raw.arrive = visualClockEvidence(arriveTimes, r, layout.arriveX0, layout.arriveX1, y, h);
                    raw.depart = visualClockEvidence(departTimes, r, layout.departX0, layout.departX1, y, h);
                    if (raw.arrive == null) {
                        raw.arrive = ocrClock(source, layout.arriveX0, layout.arriveX1, y, h, workDir, accept, 0);
                    }
                    if (raw.depart == null) {
                        raw.depart = ocrClock(source, layout.departX0, layout.departX1, y, h, workDir, accept, 0);
                    }
                    raw.name = nameAt(names, r);
                    raw.nameStruck = struckAt(names, r);
                }
                raw.headerWarehouse = header[0];
                raw.headerDateRaw = header[1];
                JSONObject record = ruleEngine.apply(raw, workingCountry, nightShift);
                RecordCountryDefaults.applyWorkingCountryPays(record, workingCountry);
                record.put("_harnessUsed", Boolean.TRUE);
                records.add(record);
                if (trace != null) {
                    JSONObject clock = new JSONObject();
                    clock.put("row", Integer.valueOf(raw.rowIndex));
                    clock.put("name", raw.name);
                    clock.put("nameStruck", Boolean.valueOf(raw.nameStruck));
                    clock.put("arrivo", raw.arrive.text);
                    clock.put("uscita", raw.depart.text);
                    clock.put("arrivoEmpty", Boolean.valueOf(!raw.arrive.hasInk));
                    clock.put("uscitaEmpty", Boolean.valueOf(!raw.depart.hasInk));
                    clock.put("Mark", record.getString("Mark"));
                    clock.put("markWhy", record.getString("_markWhy"));
                    clock.put("arrivoBbox", record.get("_arrivoBbox"));
                    clock.put("uscitaBbox", record.get("_uscitaBbox"));
                    trace.step("clock_extract", clock);
                }
            }
            dropHeaderRows(records);
            dropTrailingEmpty(records);
            dropBlankNameAndClocks(records);
            if (trace != null) {
                JSONObject done = new JSONObject();
                done.put("recordCount", Integer.valueOf(records.size()));
                done.put("marks", markCounts(records));
                done.put("elapsedMs", Long.valueOf(System.currentTimeMillis() - pageStart));
                done.put("workDir", workDir.toAbsolutePath().toString());
                done.put("debug", Boolean.valueOf(properties.isDebug()));
                done.put("cols", new int[] {tableX0, tableX1});
                done.put("rowEdges", rowEdges);
                trace.step("harness_page_done", done);
            }
            if (properties.isDebug()) {
                RecognitionDebugDumper.persist(workDir, trace, records);
            }
            return records;
        } finally {
            RecognitionDebugDumper.cleanup(workDir, properties);
        }
    }

    /**
     * 定位姓名列 + 到达/离开列。
     * 首选：视觉读表头列名得到列序号 → 投影法检测到的竖边给出精确坐标。
     * 回退：纯投影启发式（最强竖边 + 宽列）。
     */
    private TableGridDetector.Layout resolveColumns(BufferedImage source, String country,
                                                    RecognitionTrace trace) {
        try {
            List<String> headers = visionClient.readColumnHeaders(source, country);
            List<Integer> edges = TableGridDetector.detectColumnEdges(source);
            if (headers != null && !headers.isEmpty() && edges.size() >= headers.size() + 1) {
                int[] cols = TableGridDetector.pickColumnEdges(edges, headers.size());
                if (cols != null && cols.length == headers.size() + 1) {
                    int idxName = indexOfField(headers, "NAME");
                    int idxArrive = indexOfField(headers, "ARRIVEE");
                    int idxDepart = indexOfField(headers, "DEPAR");
                    if (idxArrive >= 0 && idxDepart >= 0 && idxName >= 0
                            && idxArrive < cols.length - 1 && idxDepart < cols.length - 1
                            && idxName < cols.length - 1) {
                        int nameX0 = cols[idxName];
                        int nameX1 = cols[idxName + 1];
                        int arriveX0 = cols[idxArrive];
                        int arriveX1 = cols[idxArrive + 1];
                        if (arriveX1 <= Math.max(arriveX0, idxDepart)) {
                            arriveX1 = cols[idxDepart];
                        }
                        int departX0 = cols[idxDepart];
                        int departX1 = cols[idxDepart + 1];
                        if (trace != null) {
                            JSONObject d = new JSONObject();
                            d.put("source", "header_index");
                            d.put("headers", headers);
                            d.put("edges", cols);
                            d.put("nameIndex", Integer.valueOf(idxName));
                            d.put("arriveIndex", Integer.valueOf(idxArrive));
                            d.put("departIndex", Integer.valueOf(idxDepart));
                            trace.step("column_layout", d);
                        }
                        return new TableGridDetector.Layout(nameX0, nameX1, arriveX0, arriveX1, departX0, departX1);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("表头定列失败，回退投影启发式: {}", e.getMessage());
        }
        TableGridDetector.Layout fallback = TableGridDetector.detectLayout(source);
        if (trace != null) {
            JSONObject d = new JSONObject();
            d.put("source", fallback == null ? "none" : "projection_heuristic");
            trace.step("column_layout", d);
        }
        return fallback;
    }

    private static int indexOfField(List<String> headers, String field) {
        for (int i = 0; i < headers.size(); i++) {
            if (field.equals(HarnessColumnCatalog.matchField(headers.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    /** 表体左右边界：投影竖边 min/max；不足两条时回退 detectLayout。 */
    private int[] tableBounds(BufferedImage source) {
        List<Integer> edges = TableGridDetector.detectColumnEdges(source);
        if (edges != null && edges.size() >= 2) {
            return new int[] {edges.get(0).intValue(), edges.get(edges.size() - 1).intValue()};
        }
        TableGridDetector.Layout l = TableGridDetector.detectLayout(source);
        if (l != null) {
            return new int[] {l.nameX0, Math.max(l.departX1, l.arriveX1)};
        }
        return null;
    }

    /** 整表读：crop 表体（所有列 × 表体行），视觉一次读全；失败返回 null。 */
    private JSONArray readTableBody(BufferedImage source, int x0, int x1, int[] rowEdges, int headerRows) {
        int cy = Math.max(0, rowEdges[headerRows]);
        int ch = Math.min(source.getHeight() - cy, rowEdges[rowEdges.length - 1] - cy);
        int cw = Math.min(source.getWidth() - x0, x1 - x0);
        if (cw <= 8 || ch <= 8) {
            return null;
        }
        BufferedImage crop = source.getSubimage(Math.max(0, x0), cy, cw, ch);
        try {
            return visionClient.readTableBody(crop);
        } catch (Exception e) {
            log.warn("整表读失败，回退逐列识别: {}", e.getMessage());
            return null;
        }
    }

    /** 由整表读的某行 JSON 填充 OcrRawResult 各字段。 */
    private static void fillFromTableRow(OcrRawResult raw, JSONObject row) {
        raw.noText = jsonStr(row, "no");
        raw.name = jsonStr(row, "name");
        raw.nameStruck = row.getBooleanValue("struck");
        raw.agency = jsonStr(row, "agency");
        raw.shift = jsonStr(row, "shift");
        raw.pauseText = jsonStr(row, "pause");
        raw.arrive = clockCell(jsonStr(row, "arrive"));
        raw.depart = clockCell(jsonStr(row, "depart"));
    }

    private static CellEvidence clockCell(String t) {
        CellEvidence ev = new CellEvidence();
        ev.source = "vision";
        ev.text = t == null ? "" : t.trim();
        ev.hasInk = !ev.text.isEmpty();
        ev.inkAfterLineRemoval = ev.hasInk;
        return ev;
    }

    private static String jsonStr(JSONObject o, String key) {
        Object v = o == null ? null : o.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }

    /** 视觉读整列时钟（方案 A）：crop 表体列，交给 VisionClient 逐行读 HH:MM；失败返回 null。 */
    private List<String> readClockColumnVisual(BufferedImage source, int x0, int x1, int[] rowEdges,
                                               int headerRows) {
        int cy = Math.max(0, rowEdges[headerRows]);
        int ch = Math.min(source.getHeight() - cy, rowEdges[rowEdges.length - 1] - cy);
        int cw = Math.min(source.getWidth() - x0, x1 - x0);
        if (cw <= 8 || ch <= 8) {
            return null;
        }
        BufferedImage crop = source.getSubimage(Math.max(0, x0), cy, cw, ch);
        try {
            return visionClient.readClockColumn(crop);
        } catch (Exception e) {
            log.warn("视觉读时钟列失败，回退本地 OCR: {}", e.getMessage());
            return null;
        }
    }

    /** 由视觉读到的整列 times 构造单行 CellEvidence；视觉不可用（null）或越界返回 null，触发回退。 */
    private static CellEvidence visualClockEvidence(List<String> times, int r, int x0, int x1, int y, int h) {
        if (times == null || r >= times.size()) {
            return null;
        }
        String t = times.get(r);
        CellEvidence ev = new CellEvidence();
        ev.source = "vision";
        ev.text = t == null ? "" : t.trim();
        ev.hasInk = !ev.text.isEmpty();
        ev.inkAfterLineRemoval = ev.hasInk;
        ev.bbox = new BBox(Math.max(0, x0), Math.max(0, y), Math.max(0, x1 - x0), Math.max(0, h));
        return ev;
    }

    private CellEvidence ocrClock(BufferedImage source, int x0, int x1, int y, int h, Path workDir,
                                  double accept, int pad) throws Exception {
        CellEvidence ev = new CellEvidence();
        ev.source = "ocr";
        int cx = Math.max(0, x0 + 1);
        int cw = Math.min(source.getWidth() - cx, (x1 - x0) - 2);
        int cy = Math.max(0, y + 1);
        int ch = Math.min(source.getHeight() - cy, h - 2);
        if (cw <= 8 || ch <= 8) {
            return ev;
        }
        // 时钟格均分 5 格：时十、时个、冒号、分十、分个，逐位单字符 OCR。
        int colW = cw / 5;
        char h1 = 0, h2 = 0, m1 = 0, m2 = 0;
        int inkCount = 0;
        for (int i = 0; i < 5; i++) {
            if (i == 2) {
                continue; // 冒号格固定，不 OCR
            }
            int gx0 = cx + i * colW;
            int gx1 = (i == 4) ? cx + cw : cx + (i + 1) * colW;
            if (gx1 - gx0 < 4) {
                continue;
            }
            BufferedImage cell = source.getSubimage(gx0, cy, gx1 - gx0, ch);
            char d = ocrDigit(cell, workDir, x0, y, i);
            if (d >= '0' && d <= '9') {
                inkCount++;
            }
            if (i == 0) {
                h1 = d;
            } else if (i == 1) {
                h2 = d;
            } else if (i == 3) {
                m1 = d;
            } else if (i == 4) {
                m2 = d;
            }
        }
        // 分个位上下文纠错：考勤时间分个位几乎都是 0（整点/半点），手写 0 有尾巴常被读成 3/9/5/6。
        if (isDigit(m1) && m1 == '0' && isDigit(m2) && m2 != '0') {
            m2 = '0';
        }
        if (inkCount == 4 && isDigit(h1) && isDigit(h2) && isDigit(m1) && isDigit(m2)) {
            ev.text = "" + h1 + h2 + ":" + m1 + m2;
            ev.hasInk = true;
            ev.inkAfterLineRemoval = true;
        } else if (inkCount == 0) {
            ev.text = "";
            ev.hasInk = false;
        } else {
            ev.text = "";
            ev.hasInk = true;
            ev.inkAfterLineRemoval = true;
            ev.lowConfidence = true;
        }
        ev.bbox = new BBox(cx, cy, cw, ch);
        return ev;
    }

    private char ocrDigit(BufferedImage cell, Path workDir, int x0, int y, int idx) throws Exception {
        BufferedImage zoomed = scale3x(cell);
        Path png = workDir.resolve("clock-" + x0 + "-" + y + "-" + idx + ".png");
        ImageIO.write(zoomed, "png", png.toFile());
        CellOcrResult ocr = ocrClient.ocrCell(png);
        return parseDigit(ocr.primaryLine());
    }

    static char parseDigit(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.isEmpty()) {
            return 0;
        }
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= '0' && c <= '9') {
                return c;
            }
        }
        return mapLetter(t.charAt(0));
    }

    static char mapLetter(char c) {
        if (c == 'O' || c == 'o' || c == 'D' || c == 'Q' || c == 'C' || c == 'U') {
            return '0';
        }
        if (c == 'I' || c == 'l' || c == 'i' || c == '|' || c == '(' || c == ')' || c == '/'
                || c == '\\' || c == '!' || c == 'J' || c == 'j') {
            return '1';
        }
        if (c == 'Z' || c == 'z') {
            return '2';
        }
        if (c == 'S' || c == 's') {
            return '5';
        }
        if (c == 'G' || c == 'g') {
            return '6';
        }
        if (c == 'B') {
            return '8';
        }
        return 0;
    }

    static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static BufferedImage scale3x(BufferedImage src) {
        int w = src.getWidth() * 3;
        int h = src.getHeight() * 3;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private JSONArray readNames(BufferedImage source, int nameX0, int nameX1, int[] rowEdges, Path workDir)
            throws Exception {
        if (nameX1 <= nameX0) {
            return new JSONArray();
        }
        int cx = Math.max(0, nameX0);
        int cw = Math.min(source.getWidth() - cx, nameX1 - nameX0);
        int cy = Math.max(0, rowEdges[1]);
        int ch = Math.min(source.getHeight() - cy, rowEdges[rowEdges.length - 1] - cy);
        if (cw <= 0 || ch <= 0) {
            return new JSONArray();
        }
        BufferedImage crop = source.getSubimage(cx, cy, cw, ch);
        Path png = workDir.resolve("name-column.png");
        ImageIO.write(crop, "png", png.toFile());
        try {
            JSONArray names = visionClient.describeNameColumn(crop);
            return names != null ? names : new JSONArray();
        } catch (Exception e) {
            log.warn("读姓名列失败: {}", e.getMessage());
            return new JSONArray();
        }
    }

    private String[] readHeader(BufferedImage source, Path workDir) {
        try {
            int ch = Math.min(source.getHeight(), source.getHeight() * 30 / 100);
            BufferedImage crop = source.getSubimage(0, 0, source.getWidth(), ch);
            return visionClient.describeHeader(crop);
        } catch (Exception e) {
            log.warn("读页头失败: {}", e.getMessage());
            return new String[] {"", ""};
        }
    }

    private static String nameAt(JSONArray names, int index) {
        if (names == null || index >= names.size()) {
            return "";
        }
        JSONObject o = names.getJSONObject(index);
        String t = o == null ? "" : o.getString("text");
        return t == null ? "" : t.trim();
    }

    private static boolean struckAt(JSONArray names, int index) {
        if (names == null || index >= names.size()) {
            return false;
        }
        JSONObject o = names.getJSONObject(index);
        return o != null && o.getBooleanValue("struck");
    }

    private List<JSONObject> structureFailedPage(Path workDir, RecognitionTrace trace, String workingCountry,
                                                 String promptCountry, long pageStart, String reason) {
        List<JSONObject> failed = new ArrayList<JSONObject>();
        PageLayout layout = new PageLayout();
        failed.add(structureFailed(workingCountry, layout, promptCountry));
        if (trace != null) {
            JSONObject done = new JSONObject();
            done.put("recordCount", Integer.valueOf(1));
            done.put("structureFailed", Boolean.TRUE);
            done.put("elapsedMs", Long.valueOf(System.currentTimeMillis() - pageStart));
            done.put("workDir", workDir.toAbsolutePath().toString());
            done.put("reason", reason);
            trace.step("harness_page_done", done);
        }
        if (properties.isDebug()) {
            RecognitionDebugDumper.persist(workDir, trace, failed);
        }
        return failed;
    }

    static Path writeOriginal(Path workDir, byte[] imageBytes, String filename) throws Exception {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("empty timesheet image");
        }
        Path page = workDir.resolve(pageFileName(filename));
        Files.write(page, imageBytes);
        return page;
    }

    static String pageFileName(String filename) {
        if (filename != null) {
            String lower = filename.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                return "page.jpg";
            }
            if (lower.endsWith(".webp")) {
                return "page.webp";
            }
        }
        return "page.png";
    }

    private static BufferedImage tryDecode(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return null;
        }
        try {
            return ImageIO.read(new ByteArrayInputStream(imageBytes));
        } catch (Exception e) {
            return null;
        }
    }

    static void dropHeaderRows(List<JSONObject> records) {
        if (records == null) {
            return;
        }
        while (!records.isEmpty() && isHeaderName(records.get(0).getString("NOM_PRENOM"))) {
            records.remove(0);
        }
    }

    static boolean isHeaderName(String name) {
        if (name == null) {
            return false;
        }
        String t = name.replaceAll("[^A-Za-z ]", " ").replaceAll("\\s+", " ").trim().toLowerCase();
        if (t.isEmpty()) {
            return false;
        }
        return "cognome nome".equals(t) || "nome e cognome".equals(t) || "nom prenom".equals(t)
                || "nome cognome".equals(t) || "name".equals(t) || "nome".equals(t);
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

    static void dropBlankNameAndClocks(List<JSONObject> records) {
        if (records == null) {
            return;
        }
        List<JSONObject> keep = new ArrayList<JSONObject>();
        for (int i = 0; i < records.size(); i++) {
            JSONObject rec = records.get(i);
            if (isBlankRecord(rec)) {
                continue;
            }
            keep.add(rec);
        }
        records.clear();
        records.addAll(keep);
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

    private String resolveApiKey() {
        List<String> keys = deepSeekProperties.getResolvedApiKeys();
        if (keys.isEmpty() && deepSeekProperties.getApiKey() != null) {
            return deepSeekProperties.getApiKey().trim();
        }
        return keys.isEmpty() ? "" : keys.get(0);
    }
}
