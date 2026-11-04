package com.skythedevil.ps5debugmobile;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.json.*;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(13, 18, 28);
    private static final int PANEL = Color.rgb(23, 31, 45);
    private static final int TEXT = Color.rgb(235, 241, 250);
    private static final int MUTED = Color.rgb(155, 170, 190);
    private static final int ACCENT = Color.rgb(75, 155, 255);
    private static final int GREEN = Color.rgb(60, 190, 135);
    private static final int SCAN_MAX = 16 * 1024 * 1024;
    private static final int REQ_IMPORT = 21;
    private static final int REQ_EXPORT = 22;
    private static final int REQ_EXPORT_GOLDHEN = 23;
    private static final int REQ_EXPORT_SHN = 24;
    private static final int REQ_EXPORT_MC4 = 25;
    private static final int REQ_IMPORT_ADDRESS_TABLE = 26;
    private static final int REQ_EXPORT_ADDRESS_TABLE = 27;
    private static final int REQ_EXPORT_HEX_PAGE = 28;
    private static final int REQ_IMPORT_HEX_PATCH = 29;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService cancelWorker = Executors.newSingleThreadExecutor();
    private PS5Client client;
    private boolean connected = false;
    private volatile boolean exactScanRunning = false;
    private volatile boolean exactScanCancel = false;
    private List<ProcessEntry> processes = new ArrayList<>();
    private List<MapEntry> maps = new ArrayList<>();
    private int selectedPid = -1;
    private JSONObject gameFile;
    private final Map<Integer, List<UndoPatch>> enabledCheats = new HashMap<>();

    private EditText host, port, pid, readAddress, readLength, writeAddress, writeBytes;
    private EditText scanAddress, scanLength, scanBytes,scanStringInput,scanMaxMatches;
    private Spinner scanStringEncoding;
    private EditText pointerTarget,pointerOffset,pointerDepth,pointerBudget;
    private TextView pointerResult;
    private volatile boolean pointerCancel=false;
    private List<PointerHit> lastPointerHits = new ArrayList<>();
    private int lastPointerPid=-1;
    private long lastPointerTarget=0;
    private EditText hexEditorInput;
    private TextView hexEditorInfo;
    private EditText hexTypedOffset;
    private Spinner hexTypedType;
    private TextView hexTypedResult;
    private byte[] hexEditorOriginal = new byte[0];
    private long hexEditorBase = 0;
    private EditText turboValue, turboResultStart, turboResultCount;
    private long turboResultTotal=0;
    private Spinner turboType;
    private Spinner turboCompare;
    private EditText turboStartAddress, turboStopAddress, turboAlignment;
    private CheckBox turboWritableOnly, turboIncludeExec, turboSkipSlow, turboFastAlignment;
    private TextView turboResult;
    private LinearLayout turboRows;
    private List<PS5Client.TurboResult> lastTurboResults = new ArrayList<>();
    private PS5Client scanClient;
    private boolean turboActive = false;
    private int turboValueType = 4;
    private int turboValueLength = 4;
    private EditText gameName, titleId, gameVersion, cheatName, cheatAddress, cheatBytes;
    private TextView connectionStatus, consoleInfo, processInfo, memoryResult, scanResult, cheatStatus, mapResult, debugResult, disasmResult;
    private Button importExactMatchesButton;
    private List<Long> lastExactMatches=new ArrayList<>();
    private byte[] lastExactPattern=new byte[0];
    private EditText mapFilter, disasmAddress, disasmLength, xrefTarget, breakpointIndex, breakpointAddress, watchIndex, watchAddress, threadId;
    private Spinner watchLengthSpinner, watchModeSpinner;
    private EditText stackRbp, stackRsp, stackDepth;
    private EditText registerNameInput,registerValueInput,fpuXmm0Input;
    private EditText remoteBufferSize,remoteBufferBytes,remoteCallAddress,remoteCallArguments;
    private TextView remoteBufferInfo;
    private long allocatedBufferAddress=0;
    private int allocatedBufferLength=0,allocatedBufferPid=-1;
    private byte[] lastGeneralRegisters=new byte[0],lastFpuRegisters=new byte[0];
    private int lastRegisterTid=-1;
    private TextView stackResult,debugEventResult;
    private volatile boolean debugEventListening=false;
    private ServerSocket debugEventServer;
    private final ExecutorService debugEventWorker=Executors.newSingleThreadExecutor();
    private final ExecutorService kernelLogWorker=Executors.newSingleThreadExecutor();
    private volatile boolean kernelLogRunning=false;
    private volatile Socket kernelLogSocket;
    private Button kernelLogButton;
    private TextView kernelLogView;
    private EditText asmAddress,asmSource,hookTarget,hookDetour,hookOriginal,hookLength,hookCaveAddress;
    private TextView asmResult,hookResult;
    private byte[] generatedHookPatch=new byte[0],generatedHookTrampoline=new byte[0],generatedHookOriginal=new byte[0],generatedHookCaveOriginal=new byte[0];
    private byte[] generatedAssembly=new byte[0],generatedAssemblyOriginal=new byte[0];
    private long generatedAssemblyAddress=0;
    private long generatedHookSite=0,generatedHookCave=0;
    private EditText caveMinSize,caveMaxMiB;
    private TextView caveResult;
    private Spinner processSpinner;
    private LinearLayout cheatList;
    private Button connectButton;
    private EditText addressEntryName, addressEntryAddress, addressEntryBytes;
    private Spinner addressEntryType;
    private LinearLayout addressRows;
    private boolean addressAutoRefresh=false, addressRefreshBusy=false;
    private volatile boolean freezeWriteBusy=false;
    private Button addressAutoButton;
    private final Runnable addressRefreshTick=new Runnable(){@Override public void run(){if(addressAutoRefresh){refreshAddressEntriesQuietly();addressHandler.postDelayed(this,2000);}}};
    private final List<AddressEntry> addressEntries = new ArrayList<>();
    private final Handler addressHandler = new Handler(Looper.getMainLooper());
    private final Runnable addressFreezeTick = new Runnable() { @Override public void run() { if(hasFrozenAddresses()){ refreshFrozenAddresses(); addressHandler.postDelayed(this, 1000); } } };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        buildUi();
        restoreStoredGameFile();
        log("Mobile companion v1.0 ready. PS5 and phone must be on the same reachable network.");
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(18), dp(16), dp(30));
        page.setBackgroundColor(BG);
        scroll.addView(page);
        setContentView(scroll);

        TextView title = text("PS5 DEBUG STUDIO", 23, TEXT, true);
        page.addView(title);
        TextView subtitle = text("MOBILE COMPANION  •  V1.0", 12, MUTED, true);
        subtitle.setPadding(0, dp(3), 0, dp(18));
        page.addView(subtitle);

        section(page, "01  CONSOLE CONNECTION", "Connect to the ps5debug-NG payload over your local network.");
        host = field("PS5 IP address", "192.168.1.100");
        port = field("Command port", "744");
        page.addView(host); page.addView(port);
        connectButton = button("CONNECT TO PS5", ACCENT);
        page.addView(connectButton);
        Button discoverButton = button("DISCOVER PS5 ON NETWORK", PANEL);
        page.addView(discoverButton);
        connectionStatus = text("●  DISCONNECTED", 13, MUTED, true);
        connectionStatus.setPadding(0, dp(10), 0, dp(4));
        page.addView(connectionStatus);
        Button queryConsoleInfo=button("QUERY PAYLOAD / FIRMWARE INFO",PANEL);
        page.addView(queryConsoleInfo);
        consoleInfo=text("Payload details not queried yet.",11,MUTED,false);
        consoleInfo.setTypeface(Typeface.MONOSPACE);page.addView(consoleInfo);
        queryConsoleInfo.setOnClickListener(v->queryConsoleInfo());
        connectButton.setOnClickListener(v -> connectOrDisconnect());
        discoverButton.setOnClickListener(v -> discoverConsole());

        section(page, "02  PROCESS", "Select the running game process before using memory tools or cheats.");
        processSpinner = new Spinner(this);
        processSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Connect first"}));
        page.addView(processSpinner);
        pid = field("Selected PID", "");
        page.addView(pid);
        Button refresh = button("REFRESH PROCESSES", PANEL);
        Button info = button("PROCESS DETAILS", PANEL);
        page.addView(refresh); page.addView(info);
        processInfo = text("No process selected.", 12, MUTED, false);
        page.addView(processInfo);
        refresh.setOnClickListener(v -> refreshProcesses());
        info.setOnClickListener(v -> showProcessInfo());

        processSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (pos >= 0 && pos < processes.size()) {
                    selectedPid = processes.get(pos).pid;
                    pid.setText(String.valueOf(selectedPid));
                    maps.clear();
                    enabledCheats.clear();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        section(page, "03  MEMORY TOOLS", "Read/write process memory. Addresses accept 0x values and module(name)+offset.");
        readAddress = field("Read address", "0x0");
        readLength = field("Read length (bytes)", "64");
        page.addView(readAddress); page.addView(readLength);
        Button read = button("READ MEMORY", PANEL);
        page.addView(read);
        memoryResult = text("Memory output will appear here.", 12, MUTED, false);
        memoryResult.setTypeface(Typeface.MONOSPACE);
        page.addView(memoryResult);
        read.setOnClickListener(v -> readMemory());

        section(page, "03A  PAGED HEX EDITOR", "Edit the bytes from the current read address. Pages are bounded to 4096 bytes; writes are read back for verification.");
        hexEditorInfo = text("No page loaded. Use READ MEMORY to load the current address.", 11, MUTED, false);
        page.addView(hexEditorInfo);
        hexEditorInput = new EditText(this);
        hexEditorInput.setTextColor(TEXT);
        hexEditorInput.setHintTextColor(MUTED);
        hexEditorInput.setHint("00 11 22 33 ...");
        hexEditorInput.setGravity(Gravity.TOP | Gravity.START);
        hexEditorInput.setTypeface(Typeface.MONOSPACE);
        hexEditorInput.setTextSize(13);
        hexEditorInput.setSingleLine(false);
        hexEditorInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        hexEditorInput.setMinLines(5);
        hexEditorInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        hexEditorInput.setBackgroundColor(PANEL);
        page.addView(hexEditorInput, new LinearLayout.LayoutParams(-1, dp(150)));
        LinearLayout hexNav = new LinearLayout(this);
        hexNav.setOrientation(LinearLayout.HORIZONTAL);
        Button hexPrevious = button("PREVIOUS PAGE", PANEL);
        Button hexNext = button("NEXT PAGE", PANEL);
        hexNav.addView(hexPrevious, new LinearLayout.LayoutParams(0, dp(46), 1));
        hexNav.addView(hexNext, new LinearLayout.LayoutParams(0, dp(46), 1));
        page.addView(hexNav);
        LinearLayout hexFileActions=new LinearLayout(this);hexFileActions.setOrientation(LinearLayout.HORIZONTAL);
        Button importHexPatch=button("LOAD PATCH FILE (.BIN)",PANEL),exportHexPage=button("EXPORT PAGE (.BIN)",PANEL);
        hexFileActions.addView(importHexPatch,new LinearLayout.LayoutParams(0,dp(46),1));
        hexFileActions.addView(exportHexPage,new LinearLayout.LayoutParams(0,dp(46),1));page.addView(hexFileActions);
        importHexPatch.setOnClickListener(v->importHexPatchFile());
        exportHexPage.setOnClickListener(v->exportHexEditorPage());
        LinearLayout hexActions = new LinearLayout(this);
        hexActions.setOrientation(LinearLayout.HORIZONTAL);
        Button hexWrite = button("WRITE + VERIFY", ACCENT);
        Button hexRestore = button("RESTORE ORIGINAL", PANEL);
        hexActions.addView(hexWrite, new LinearLayout.LayoutParams(0, dp(46), 1));
        hexActions.addView(hexRestore, new LinearLayout.LayoutParams(0, dp(46), 1));
        page.addView(hexActions);
        hexPrevious.setOnClickListener(v -> moveHexEditorPage(-1));
        hexNext.setOnClickListener(v -> moveHexEditorPage(1));
        hexWrite.setOnClickListener(v -> writeHexEditorPage(false));
        hexRestore.setOnClickListener(v -> writeHexEditorPage(true));

        section(page, "03A1  TYPED VALUE INSPECTOR", "Interpret bytes at an offset in the loaded or edited hex page. This is read-only; use WRITE + VERIFY to commit edits.");
        hexTypedOffset=field("Byte offset from page start", "0");
        hexTypedType=new Spinner(this);
        String[] hexTypes={"Byte unsigned","Byte signed","2-byte unsigned","2-byte signed","4-byte unsigned","4-byte signed","8-byte unsigned","8-byte signed","Float","Double","UTF-8 (remaining page)","UTF-16LE (remaining page)"};
        hexTypedType.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,hexTypes));
        page.addView(hexTypedOffset); page.addView(hexTypedType);
        Button inspectTyped=button("INSPECT TYPED VALUE",PANEL);
        page.addView(inspectTyped);
        hexTypedResult=text("Load a page, choose an offset and type, then inspect.",12,MUTED,false);
        hexTypedResult.setTypeface(Typeface.MONOSPACE); page.addView(hexTypedResult);
        inspectTyped.setOnClickListener(v->inspectHexTypedValue());

        section(page, "03C  POINTER SCANNER", "Build a bounded pointer map from readable regions (module regions prioritized), then search multi-level chains back to module memory.");
        pointerTarget=field("Target address", "0x0");
        pointerOffset=field("Maximum offset per level (hex or decimal)", "4096");
        pointerDepth=field("Maximum depth (1–5)", "3");
        pointerBudget=field("Memory scan budget (MiB; 1–256)", "64");
        page.addView(pointerTarget); page.addView(pointerOffset); page.addView(pointerDepth); page.addView(pointerBudget);
        LinearLayout pointerActions=new LinearLayout(this); pointerActions.setOrientation(LinearLayout.HORIZONTAL);
        Button pointerScan=button("BUILD MAP + FIND CHAINS",ACCENT), pointerCancelButton=button("CANCEL",PANEL);
        pointerActions.addView(pointerScan,new LinearLayout.LayoutParams(0,dp(48),1));
        pointerActions.addView(pointerCancelButton,new LinearLayout.LayoutParams(0,dp(48),1)); page.addView(pointerActions);
        LinearLayout pointerSavedActions=new LinearLayout(this);pointerSavedActions.setOrientation(LinearLayout.HORIZONTAL);
        Button savePointerResults=button("SAVE CHAINS",PANEL),loadPointerResults=button("LOAD SAVED CHAINS",PANEL),resolvePointerResults=button("RESOLVE LIVE CHAINS",PANEL);
        pointerSavedActions.addView(savePointerResults,new LinearLayout.LayoutParams(0,dp(48),1));
        pointerSavedActions.addView(loadPointerResults,new LinearLayout.LayoutParams(0,dp(48),1));page.addView(pointerSavedActions);
        page.addView(resolvePointerResults);
        savePointerResults.setOnClickListener(v->savePointerChains());
        loadPointerResults.setOnClickListener(v->loadPointerChains());
        resolvePointerResults.setOnClickListener(v->resolvePointerChains());
        pointerResult=text("No pointer scan performed. Readable memory; module regions prioritized; bounded by the MiB budget.",11,MUTED,false);
        pointerResult.setTypeface(Typeface.MONOSPACE); page.addView(pointerResult);
        pointerScan.setOnClickListener(v->startPointerScan());
        pointerCancelButton.setOnClickListener(v->{pointerCancel=true;pointerResult.setText("Cancellation requested…");});

        writeAddress = field("Write address", "0x0");
        writeBytes = field("Bytes to write (hex)", "90 90 90");
        page.addView(writeAddress); page.addView(writeBytes);
        Button write = button("WRITE MEMORY", PANEL);
        page.addView(write);
        write.setOnClickListener(v -> writeMemory());

        section(page, "03B  ADDRESS LIST & FREEZE", "Save memory addresses, refresh values, edit bytes and freeze selected entries at one-second intervals.");
        addressEntryName=field("Entry name", "Money");
        addressEntryAddress=field("Address", "0x0");
        addressEntryType=new Spinner(this); String[] addressTypes={"Hex bytes","Byte (unsigned)","Byte (signed)","2 bytes (unsigned)","2 bytes (signed)","4 bytes (unsigned)","4 bytes (signed)","8 bytes (unsigned)","8 bytes (signed)","Float","Double"}; addressEntryType.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,addressTypes));
        addressEntryBytes=field("Value (hex bytes or typed number)", "00 00 00 00");
        page.addView(addressEntryName); page.addView(addressEntryAddress); page.addView(addressEntryType); page.addView(addressEntryBytes);
        LinearLayout addressActions=new LinearLayout(this); addressActions.setOrientation(LinearLayout.HORIZONTAL);
        Button addAddress=button("ADD ADDRESS", ACCENT), refreshAddresses=button("REFRESH ALL", PANEL); addressAutoButton=button("AUTO REFRESH: OFF", PANEL);
        addressActions.addView(addAddress,new LinearLayout.LayoutParams(0,-2,1)); addressActions.addView(refreshAddresses,new LinearLayout.LayoutParams(0,-2,1)); page.addView(addressActions); page.addView(addressAutoButton);
        LinearLayout addressFileActions=new LinearLayout(this);addressFileActions.setOrientation(LinearLayout.HORIZONTAL);
        Button importAddressTable=button("LOAD TABLE",PANEL),exportAddressTable=button("SAVE TABLE",PANEL);
        addressFileActions.addView(importAddressTable,new LinearLayout.LayoutParams(0,dp(44),1));
        addressFileActions.addView(exportAddressTable,new LinearLayout.LayoutParams(0,dp(44),1));
        page.addView(addressFileActions);
        importAddressTable.setOnClickListener(v->importAddressTable());
        exportAddressTable.setOnClickListener(v->exportAddressTable());
        addressRows=new LinearLayout(this); addressRows.setOrientation(LinearLayout.VERTICAL); page.addView(addressRows);
        addAddress.setOnClickListener(v->addAddressEntry());
        refreshAddresses.setOnClickListener(v->refreshAddressEntries());
        addressAutoButton.setOnClickListener(v->{addressAutoRefresh=!addressAutoRefresh;addressAutoButton.setText(addressAutoRefresh?"AUTO REFRESH: ON":"AUTO REFRESH: OFF");addressHandler.removeCallbacks(addressRefreshTick);if(addressAutoRefresh)addressHandler.post(addressRefreshTick);});
        LinearLayout freezeBulkRow=new LinearLayout(this);freezeBulkRow.setOrientation(LinearLayout.HORIZONTAL);
        Button freezeAll=button("FREEZE ALL",PANEL),unfreezeAll=button("UNFREEZE ALL",PANEL);
        freezeBulkRow.addView(freezeAll,new LinearLayout.LayoutParams(0,dp(46),1));
        freezeBulkRow.addView(unfreezeAll,new LinearLayout.LayoutParams(0,dp(46),1));
        page.addView(freezeBulkRow);
        freezeAll.setOnClickListener(v->setAllAddressFrozen(true));
        unfreezeAll.setOnClickListener(v->setAllAddressFrozen(false));
        loadAddressEntries();

        section(page, "04  BYTE / AOB SCAN", "Scans a chosen range for exact bytes or wildcard AOB signatures (use ?? for any byte). Each scan is capped at 16 MiB.");
        scanAddress = field("Start address", "0x0");
        scanLength = field("Range size (max 16 MiB)", "1048576");
        scanBytes = field("Pattern (hex; ?? = wildcard)", "DE AD ?? EF");
        page.addView(scanAddress); page.addView(scanLength); page.addView(scanBytes);
        scanMaxMatches=field("Maximum matches to collect (1–2000)","500");page.addView(scanMaxMatches);
        scanStringInput=field("Text string to scan (UTF-8 / UTF-16LE)","");
        scanStringEncoding=new Spinner(this);
        scanStringEncoding.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"UTF-8","UTF-16LE"}));
        page.addView(scanStringInput);page.addView(scanStringEncoding);
        Button scanString=button("ENCODE STRING + SCAN",PANEL);page.addView(scanString);
        scanString.setOnClickListener(v->scanStringPattern());
        LinearLayout exactScanActions = new LinearLayout(this);
        Button scan = button("SCAN RANGE", PANEL);
        Button cancelExactScan = button("CANCEL SCAN", PANEL);
        exactScanActions.addView(scan, new LinearLayout.LayoutParams(0, dp(48), 1));
        exactScanActions.addView(cancelExactScan, new LinearLayout.LayoutParams(0, dp(48), 1));
        page.addView(exactScanActions);
        cancelExactScan.setOnClickListener(v -> {
            if (!exactScanRunning) { toast("No exact byte scan is running."); return; }
            exactScanCancel = true;
            log("Exact byte scan cancellation requested.");
        });
        scanResult = text("No scan performed.", 12, MUTED, false);
        scanResult.setTypeface(Typeface.MONOSPACE);
        page.addView(scanResult);
        importExactMatchesButton=button("ADD MATCHES TO ADDRESS TABLE",PANEL);page.addView(importExactMatchesButton);
        importExactMatchesButton.setEnabled(false);
        importExactMatchesButton.setOnClickListener(v->importExactMatchesToAddressTable());
        scan.setOnClickListener(v -> scanRange());

        section(page, "04B  TURBO VALUE SCANNER", "Uses the payload's server-side TurboScan engine on a separate connection. Start with an exact value, refine with Next Scan, then view results.");
        turboType = new Spinner(this);
        String[] turboTypes = {"Byte (unsigned)", "Byte (signed)", "2 bytes (unsigned)", "2 bytes (signed)", "4 bytes (unsigned)", "4 bytes (signed)", "8 bytes (unsigned)", "8 bytes (signed)", "Float", "Double"};
        turboType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, turboTypes));
        turboType.setSelection(4);
        page.addView(turboType);
        turboStartAddress=field("Memory start address (blank = all)", ""); turboStopAddress=field("Memory stop address (blank = all)", "");
        page.addView(turboStartAddress); page.addView(turboStopAddress);
        turboWritableOnly=new CheckBox(this); turboWritableOnly.setText("Writable regions only"); turboWritableOnly.setTextColor(TEXT); turboWritableOnly.setChecked(true); page.addView(turboWritableOnly);
        turboIncludeExec=new CheckBox(this); turboIncludeExec.setText("Include executable/code regions"); turboIncludeExec.setTextColor(TEXT); page.addView(turboIncludeExec);
        turboSkipSlow=new CheckBox(this); turboSkipSlow.setText("Skip slow / uncached regions (GPU)"); turboSkipSlow.setTextColor(TEXT); page.addView(turboSkipSlow);
        turboFastAlignment=new CheckBox(this); turboFastAlignment.setText("Fast aligned scan"); turboFastAlignment.setTextColor(TEXT); turboFastAlignment.setChecked(true); page.addView(turboFastAlignment);
        turboAlignment=field("Alignment (1, 2, 4, 8, 16)", "4"); page.addView(turboAlignment);
        turboCompare = new Spinner(this);
        String[] compareLabels = {"Exact value", "Fuzzy (±1)", "Bigger than", "Smaller than", "Between low/high", "Increased", "Increased by", "Decreased", "Decreased by", "Changed", "Unchanged", "Unknown initial value"};
        turboCompare.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, compareLabels));
        page.addView(turboCompare);
        turboValue = field("Search value (Between: low,high)", "100"); page.addView(turboValue);
        turboResultStart=field("Result start index (0 = first match)", "0"); turboResultCount=field("Results per page (maximum 500)", "100");
        page.addView(turboResultStart); page.addView(turboResultCount);
        TextView turboPagingHelp=text("Search value = the number to find. Start index = where the result list begins (0, 100, 200...). Results per page = how many matches to load at once.",11,MUTED,false);
        turboPagingHelp.setPadding(dp(2),dp(2),dp(2),dp(8)); page.addView(turboPagingHelp);
        LinearLayout turboPageNav=new LinearLayout(this); turboPageNav.setOrientation(LinearLayout.HORIZONTAL);
        Button turboPrevPage=button("PREVIOUS PAGE",PANEL), turboNextPage=button("NEXT PAGE",PANEL), turboReloadPage=button("RELOAD PAGE",PANEL);
        turboPageNav.addView(turboPrevPage,new LinearLayout.LayoutParams(0,dp(48),1));
        turboPageNav.addView(turboNextPage,new LinearLayout.LayoutParams(0,dp(48),1));
        turboPageNav.addView(turboReloadPage,new LinearLayout.LayoutParams(0,dp(48),1));
        page.addView(turboPageNav);
        Button saveTurboPage=button("SAVE CURRENT PAGE TO ADDRESS TABLE",PANEL);page.addView(saveTurboPage);
        saveTurboPage.setOnClickListener(v->saveTurboPageToAddressTable());
        Button turboFirst = button("TURBO FIRST SCAN", ACCENT);
        Button turboNext = button("NEXT SCAN (EXACT VALUE)", PANEL);
        Button turboGet = button("LOAD RESULTS PAGE", PANEL);
        Button turboEnd = button("END SCAN SESSION", PANEL);
        Button turboCancel = button("CANCEL RUNNING SCAN", PANEL);
        page.addView(turboFirst); page.addView(turboNext); page.addView(turboGet); page.addView(turboEnd); page.addView(turboCancel);
        turboResult = text("TurboScan not started. Requires payload TurboScan support.", 11, MUTED, false);
        turboResult.setTypeface(Typeface.MONOSPACE); page.addView(turboResult);
        turboRows=new LinearLayout(this); turboRows.setOrientation(LinearLayout.VERTICAL); page.addView(turboRows);
        turboFirst.setOnClickListener(v -> turboFirstScan());
        turboNext.setOnClickListener(v -> turboNextScan());
        turboGet.setOnClickListener(v -> turboLoadResults());
        turboPrevPage.setOnClickListener(v -> moveTurboPage(-1));
        turboNextPage.setOnClickListener(v -> moveTurboPage(1));
        turboReloadPage.setOnClickListener(v -> turboLoadResults());
        turboEnd.setOnClickListener(v -> turboEndScan());
        turboCancel.setOnClickListener(v -> turboCancelScan());

        section(page, "05  MEMORY MAPS & CODE INSPECTION", "Inspect mapped modules/regions, decode x86-64 instruction boundaries using the payload, and search RIP-relative cross-references.");
        mapFilter = field("Optional module/name filter", "");
        page.addView(mapFilter);
        Button loadMaps = button("LOAD PROCESS MAPS", PANEL);
        page.addView(loadMaps);
        mapResult = text("Memory maps not loaded.", 11, MUTED, false);
        mapResult.setTypeface(Typeface.MONOSPACE);
        page.addView(mapResult);
        disasmAddress = field("Disassembly address", "0x0");
        disasmLength = field("Bytes to decode (max 4096)", "256");
        page.addView(disasmAddress); page.addView(disasmLength);
        Button disassemble = button("DISASSEMBLE REGION", PANEL);
        page.addView(disassemble);
        disasmResult = text("No disassembly loaded.", 11, MUTED, false);
        disasmResult.setTypeface(Typeface.MONOSPACE);
        page.addView(disasmResult);
        xrefTarget = field("Find references to address", "0x0");
        page.addView(xrefTarget);
        Button xrefs = button("FIND RIP-RELATIVE REFERENCES", PANEL);
        page.addView(xrefs);
        loadMaps.setOnClickListener(v -> loadMaps());
        disassemble.setOnClickListener(v -> disassembleRegion());
        xrefs.setOnClickListener(v -> findXrefs());
        section(page, "05B  CODE CAVE FINDER", "Find executable memory padding that may be suitable for controlled code placement. This is discovery only; no bytes are modified.");
        caveMinSize=field("Minimum cave length (bytes)","16");
        caveMaxMiB=field("Scan budget (MiB; max 128)","32");
        page.addView(caveMinSize);page.addView(caveMaxMiB);
        Button caveScan=button("SCAN EXECUTABLE CODE CAVES",PANEL);page.addView(caveScan);
        caveResult=text("No code-cave scan performed.",11,MUTED,false);caveResult.setTypeface(Typeface.MONOSPACE);page.addView(caveResult);
        caveScan.setOnClickListener(v->scanCodeCaves());


        section(page, "05C  X64 ASSEMBLER + HOOK BUILDER", "Offline x86-64 helper. Assemble the supported instruction subset or generate a 14-byte absolute JMP patch and trampoline. Review instruction boundaries before using a hook.");
        asmAddress=field("Assembly base address","0x0");
        asmSource=field("Assembly instructions (one per line; subset listed below)","nop\\nmov rax, 0x1122334455667788\\nret");
        asmSource.setSingleLine(false);asmSource.setMinLines(4);asmSource.setGravity(Gravity.TOP|Gravity.START);
        page.addView(asmAddress);page.addView(asmSource);
        Button asmBuild=button("ASSEMBLE TO HEX",PANEL);page.addView(asmBuild);
        Button asmWrite=button("WRITE ASSEMBLED CODE + VERIFY",ACCENT);page.addView(asmWrite);
        Button asmRestore=button("RESTORE PREVIOUS BYTES",PANEL);page.addView(asmRestore);
        asmResult=text("Supported: nop, ret, int3, push/pop 64-bit registers, mov r64, imm64, jmp/call rel32, db hex bytes.",11,MUTED,false);
        asmResult.setTypeface(Typeface.MONOSPACE);asmResult.setTextIsSelectable(true);page.addView(asmResult);
        hookTarget=field("Hook site address","0x0");
        hookDetour=field("Detour address","0x0");
        hookLength=field("Stolen whole-instruction length (14–64 bytes)","14");
        hookOriginal=field("Original bytes at hook site (hex)","");
        hookCaveAddress=field("Executable cave address for trampoline","0x0");
        hookOriginal.setSingleLine(false);hookOriginal.setMinLines(2);hookOriginal.setGravity(Gravity.TOP|Gravity.START);
        page.addView(hookTarget);page.addView(hookDetour);page.addView(hookLength);page.addView(hookOriginal);
        Button hookRead=button("READ ORIGINAL BYTES FROM PS5",PANEL);page.addView(hookRead);
        page.addView(hookCaveAddress);
        Button hookBuild=button("GENERATE HOOK PATCH + TRAMPOLINE",PANEL);page.addView(hookBuild);
        Button hookInstall=button("WRITE TRAMPOLINE + APPLY HOOK (VERIFY)",ACCENT);page.addView(hookInstall);
        Button hookRestore=button("RESTORE ORIGINAL HOOK-SITE BYTES",PANEL);page.addView(hookRestore);
        hookResult=text("No hook generated. This helper does not write memory.",11,MUTED,false);
        hookResult.setTypeface(Typeface.MONOSPACE);hookResult.setTextIsSelectable(true);page.addView(hookResult);
        asmBuild.setOnClickListener(v->assembleMiniUi());
        asmWrite.setOnClickListener(v->writeAssembledCode());
        asmRestore.setOnClickListener(v->restoreAssembledCode());
        hookRead.setOnClickListener(v->readHookOriginal());
        hookBuild.setOnClickListener(v->buildAbsoluteHook());
        hookInstall.setOnClickListener(v->installGeneratedHook());
        hookRestore.setOnClickListener(v->restoreGeneratedHook());

        section(page, "06  DEBUGGER", "Attach/detach, software breakpoints, hardware watchpoints, thread listing, register inspection, single-step and TCP 755 live event listener.");
        breakpointIndex = field("Breakpoint slot (0–29)", "0");
        breakpointAddress = field("Breakpoint address", "0x0");
        page.addView(breakpointIndex); page.addView(breakpointAddress);
        LinearLayout bpRow = new LinearLayout(this);
        Button bpOn = button("SET BREAKPOINT", PANEL);
        Button bpOff = button("CLEAR BREAKPOINT", PANEL);
        bpRow.addView(bpOn, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams bpRight = new LinearLayout.LayoutParams(0, dp(48), 1); bpRight.setMargins(dp(6),0,0,0);
        bpRow.addView(bpOff, bpRight); page.addView(bpRow);
        watchIndex = field("Watchpoint slot (0–3)", "0");
        watchAddress = field("Watch address", "0x0");
        watchLengthSpinner=new Spinner(this);
        watchLengthSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"1 byte","2 bytes","4 bytes","8 bytes"}));
        watchModeSpinner=new Spinner(this);
        watchModeSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Write access","Read / write access"}));
        page.addView(watchIndex); page.addView(watchAddress); page.addView(watchLengthSpinner); page.addView(watchModeSpinner);
        Button watchOn = button("SET HARDWARE WATCHPOINT", PANEL);
        Button watchOff = button("CLEAR WATCHPOINT", PANEL);
        page.addView(watchOn); page.addView(watchOff);
        Button attach = button("ATTACH DEBUGGER", ACCENT);
        Button detach = button("DETACH DEBUGGER", PANEL);
        Button threads = button("LIST THREADS", PANEL);
        threadId = field("Thread/LWP ID", "");
        Button regs = button("READ GENERAL REGISTERS", PANEL);
        registerNameInput=field("Register name (e.g. rax, rip, rsp)", "rax");
        registerValueInput=field("New register value (decimal or 0x hex)", "0x0");
        Button writeRegister=button("WRITE REGISTER + VERIFY",PANEL);
        Button readFpu=button("READ FPU / XMM / YMM STATE",PANEL);
        fpuXmm0Input=field("XMM0 value (32 hex digits)", "");
        Button writeXmm0=button("WRITE XMM0 + VERIFY",PANEL);
        section(page,"06C  REMOTE MEMORY BUFFER","Allocate/free a target-process buffer and write data with read-back verification. Free only buffers allocated by this session.");
        remoteBufferSize=field("Buffer size in bytes (1–1 MiB)","256");
        remoteBufferBytes=field("Buffer data (hex)","00 00 00 00");
        page.addView(remoteBufferSize);page.addView(remoteBufferBytes);
        LinearLayout bufferActions=new LinearLayout(this);bufferActions.setOrientation(LinearLayout.HORIZONTAL);
        Button allocBuffer=button("ALLOCATE",PANEL),writeBuffer=button("WRITE + VERIFY",PANEL),freeBuffer=button("FREE",PANEL);
        bufferActions.addView(allocBuffer,new LinearLayout.LayoutParams(0,dp(46),1));
        bufferActions.addView(writeBuffer,new LinearLayout.LayoutParams(0,dp(46),1));
        bufferActions.addView(freeBuffer,new LinearLayout.LayoutParams(0,dp(46),1));
        page.addView(bufferActions);
        remoteBufferInfo=text("No remote buffer allocated.",11,MUTED,false);remoteBufferInfo.setTypeface(Typeface.MONOSPACE);page.addView(remoteBufferInfo);
        remoteCallAddress=field("Function address (must be executable)", "module(eboot.bin)+0x0");
        remoteCallArguments=field("Six SysV args: rdi,rsi,rdx,rcx,r8,r9 (comma-separated)", "0,0,0,0,0,0");
        page.addView(remoteCallAddress);page.addView(remoteCallArguments);
        Button callFunction=button("CALL FUNCTION (STACK-SAFE TRAMPOLINE)",ACCENT);page.addView(callFunction);
        TextView callWarning=text("Warning: wrong function addresses or arguments can still crash the game. Calls are never automatically retried.",10,MUTED,false);page.addView(callWarning);
        callFunction.setOnClickListener(v->callRemoteFunction());
        allocBuffer.setOnClickListener(v->allocateRemoteBuffer());
        writeBuffer.setOnClickListener(v->writeRemoteBuffer());
        freeBuffer.setOnClickListener(v->freeRemoteBuffer());

        Button step = button("SINGLE STEP PROCESS", PANEL);
        Button stepThread=button("STEP SELECTED THREAD",PANEL);
        LinearLayout runControl=new LinearLayout(this);runControl.setOrientation(LinearLayout.HORIZONTAL);
        Button pauseProcess=button("PAUSE PROCESS",PANEL),resumeProcess=button("RESUME PROCESS",PANEL);
        runControl.addView(pauseProcess,new LinearLayout.LayoutParams(0,dp(46),1));
        runControl.addView(resumeProcess,new LinearLayout.LayoutParams(0,dp(46),1));
        page.addView(attach); page.addView(detach); page.addView(threads); page.addView(threadId); page.addView(regs);
        page.addView(registerNameInput);page.addView(registerValueInput);page.addView(writeRegister);
        page.addView(readFpu);page.addView(fpuXmm0Input);page.addView(writeXmm0);page.addView(step);page.addView(stepThread);page.addView(runControl);
        stepThread.setOnClickListener(v->debugStepThread());
        pauseProcess.setOnClickListener(v->setProcessPaused(true));
        resumeProcess.setOnClickListener(v->setProcessPaused(false));
        writeRegister.setOnClickListener(v->writeGeneralRegister());
        readFpu.setOnClickListener(v->readFpuRegisters());
        writeXmm0.setOnClickListener(v->writeXmm0());
        section(page, "06A  LIVE DEBUG EVENTS", "Listen on phone TCP port 755 before attaching. The payload connects back to this device with breakpoint, watchpoint and single-step register snapshots.");
        Button eventListen=button("START / RESTART EVENT LISTENER (TCP 755)",PANEL);page.addView(eventListen);
        debugEventResult=text("Listener stopped. Start it before attaching the debugger.",11,MUTED,false);debugEventResult.setTypeface(Typeface.MONOSPACE);page.addView(debugEventResult);
        eventListen.setOnClickListener(v->startDebugEventListener());
        section(page, "06B  CALL STACK", "Walk the frame-pointer chain using the PS5 payload. Leave RBP/RSP blank to use the selected thread's live registers.");
        stackRbp=field("RBP (optional; auto from registers)","");
        stackRsp=field("RSP (optional; auto from registers)","");
        stackDepth=field("Maximum frames (1–64)","16");
        page.addView(stackRbp);page.addView(stackRsp);page.addView(stackDepth);
        Button stackRead=button("READ CALL STACK",PANEL);page.addView(stackRead);
        stackResult=text("No stack loaded.",11,MUTED,false);stackResult.setTypeface(Typeface.MONOSPACE);page.addView(stackResult);
        stackRead.setOnClickListener(v->readCallStack());
        debugResult = text("Debugger detached.", 11, MUTED, false);
        debugResult.setTypeface(Typeface.MONOSPACE);
        page.addView(debugResult);
        bpOn.setOnClickListener(v -> setBreakpoint(true));
        bpOff.setOnClickListener(v -> setBreakpoint(false));
        watchOn.setOnClickListener(v -> setWatchpoint(true));
        watchOff.setOnClickListener(v -> setWatchpoint(false));
        attach.setOnClickListener(v -> debugAttach(true));
        detach.setOnClickListener(v -> debugAttach(false));
        threads.setOnClickListener(v -> listThreads());
        regs.setOnClickListener(v -> readRegisters());
        step.setOnClickListener(v -> debugStep());

        section(page, "07  CHEAT LIBRARY", "Imports/exports ps5cheat/1 and converts fixed-patch GoldHEN JSON. Other cheat formats are not supported yet.");
        gameName = field("Game name", "");
        titleId = field("Title ID (e.g. PPSA01341)", "");
        gameVersion = field("Game version", "");
        cheatName = field("New cheat name", "Infinite Health");
        cheatAddress = field("Patch address", "module(eboot.bin)+0x0");
        cheatBytes = field("Patch bytes (hex)", "90 90 90 90");
        page.addView(gameName); page.addView(titleId); page.addView(gameVersion);
        Button create = button("CREATE / UPDATE GAME FILE", PANEL);
        page.addView(create);
        page.addView(cheatName); page.addView(cheatAddress); page.addView(cheatBytes);
        Button addPatch = button("ADD PATCH CHEAT", ACCENT);
        page.addView(addPatch);
        LinearLayout fileButtons = new LinearLayout(this);
        fileButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button importFile = button("IMPORT JSON", PANEL);
        Button exportFile = button("EXPORT JSON", PANEL);
        Button exportGoldHenFile=button("EXPORT GOLDHEN",PANEL);
        Button exportShnFile=button("EXPORT SHN",PANEL);
        Button exportMc4File=button("EXPORT MC4",PANEL);
        fileButtons.addView(importFile, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(48), 1);
        right.setMargins(dp(8), 0, 0, 0);
        fileButtons.addView(exportFile, right);
        page.addView(fileButtons);
        page.addView(exportGoldHenFile);
        page.addView(exportShnFile);
        page.addView(exportMc4File);
        cheatStatus = text("No game file loaded.", 12, MUTED, false);
        cheatStatus.setPadding(0, dp(10), 0, dp(6));
        page.addView(cheatStatus);
        Button turnOffAll=button("TURN ALL ACTIVE CHEATS OFF",PANEL);page.addView(turnOffAll);
        turnOffAll.setOnClickListener(v->turnAllCheatsOff());
        cheatList = new LinearLayout(this);
        cheatList.setOrientation(LinearLayout.VERTICAL);
        page.addView(cheatList);
        create.setOnClickListener(v -> createGameFile());
        addPatch.setOnClickListener(v -> addPatchCheat());
        importFile.setOnClickListener(v -> importJson());
        exportFile.setOnClickListener(v -> exportJson());
        exportGoldHenFile.setOnClickListener(v->exportGoldHenJson());
        exportShnFile.setOnClickListener(v->exportShn());
        exportMc4File.setOnClickListener(v->exportMc4());

        section(page, "08  KERNEL LOG STREAM", "Live ps5debug-NG kernel log from TCP port 3232. Start and stop without blocking the app.");
        kernelLogButton=button("START KERNEL LOG (TCP 3232)",PANEL);page.addView(kernelLogButton);
        kernelLogView=text("Kernel log is stopped.",10,MUTED,false);kernelLogView.setTypeface(Typeface.MONOSPACE);
        kernelLogView.setPadding(dp(8),dp(8),dp(8),dp(8));kernelLogView.setBackgroundColor(PANEL);
        page.addView(kernelLogView,new LinearLayout.LayoutParams(-1,dp(190)));
        kernelLogButton.setOnClickListener(v->{if(kernelLogRunning)stopKernelLog();else startKernelLog();});

        section(page, "08A  AI CHEAT CREATOR", "Connect an AI provider once, then work through cheat discovery conversationally. The assistant can plan scans and interpret user-reported values; live screen capture and autonomous scan control are separate integrations.");
        aiStatusView=text(AiAssistant.isConfigured(this)?"AI CONFIGURED  •  API key stored encrypted on this device.":"AI NOT CONFIGURED  •  Save a provider endpoint, model and API key.",11,MUTED,true);
        page.addView(aiStatusView);
        TextView providerLabel=text("AI PROVIDER",10,MUTED,true);page.addView(providerLabel);
        Spinner aiProviderSpinner=new Spinner(this);
        String[] aiProviders={"OpenAI","OpenRouter","Custom OpenAI-compatible"};
        aiProviderSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,aiProviders));
        String savedEndpoint=AiAssistant.endpoint(this);
        int savedProvider=savedEndpoint.toLowerCase(Locale.US).contains("openrouter.ai")?1:
            (savedEndpoint.toLowerCase(Locale.US).contains("api.openai.com")?0:2);
        aiProviderSpinner.setSelection(savedProvider);page.addView(aiProviderSpinner);
        aiEndpointInput=field("API base URL",AiAssistant.endpoint(this));page.addView(aiEndpointInput);
        aiModelInput=field("Model name (OpenRouter: provider/model)",AiAssistant.model(this));page.addView(aiModelInput);
        aiProviderSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            private int lastSelection=savedProvider;
            @Override public void onNothingSelected(AdapterView<?> parent){}
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
                if(position==lastSelection)return;
                lastSelection=position;
                if(position==0){
                    aiEndpointInput.setText("https://api.openai.com/v1");
                    aiModelInput.setText("gpt-4o-mini");
                }else if(position==1){
                    aiEndpointInput.setText("https://openrouter.ai/api/v1");
                    aiModelInput.setText("openai/gpt-4o-mini");
                }
                // Custom preserves the current endpoint and model for manual editing.
            }
        });
        aiKeyInput=field("API key (blank keeps the saved key)","");
        aiKeyInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        page.addView(aiKeyInput);
        LinearLayout aiSettingsRow=new LinearLayout(this);aiSettingsRow.setOrientation(LinearLayout.HORIZONTAL);
        aiSaveButton=button("SAVE SETTINGS + TEST",ACCENT);
        Button aiClear=button("DISCONNECT AI",PANEL);
        aiSettingsRow.addView(aiSaveButton,new LinearLayout.LayoutParams(0,dp(48),1));
        aiSettingsRow.addView(aiClear,new LinearLayout.LayoutParams(0,dp(48),1));page.addView(aiSettingsRow);
        aiChatView=text("AI conversation will appear here. Configure a provider to begin.",12,TEXT,false);
        aiChatView.setTypeface(Typeface.MONOSPACE);aiChatView.setTextIsSelectable(true);
        aiChatView.setPadding(dp(10),dp(10),dp(10),dp(10));aiChatView.setBackgroundColor(PANEL);
        page.addView(aiChatView,new LinearLayout.LayoutParams(-1,dp(260)));
        aiPromptInput=field("Tell AI which cheat you want to create…","");
        aiPromptInput.setSingleLine(false);aiPromptInput.setMinLines(2);aiPromptInput.setGravity(Gravity.TOP|Gravity.START);
        page.addView(aiPromptInput,new LinearLayout.LayoutParams(-1,dp(84)));
        aiSendButton=button("SEND TO AI ASSISTANT",ACCENT);page.addView(aiSendButton);
        LinearLayout aiScanRow=new LinearLayout(this);aiScanRow.setOrientation(LinearLayout.HORIZONTAL);
        Button aiRunScan=button("AI-APPROVED SCAN",PANEL),aiShareResults=button("SHARE RESULTS WITH AI",PANEL);
        aiScanRow.addView(aiRunScan,new LinearLayout.LayoutParams(0,dp(48),1));
        aiScanRow.addView(aiShareResults,new LinearLayout.LayoutParams(0,dp(48),1));page.addView(aiScanRow);
        aiRunScan.setOnClickListener(v->runAiApprovedTurboScan());
        aiShareResults.setOnClickListener(v->shareTurboResultsWithAi());
        Button aiVerifyCandidates=button("VERIFY LOADED CANDIDATES (READ ONLY)",PANEL);page.addView(aiVerifyCandidates);
        aiVerifyCandidates.setOnClickListener(v->verifyTurboCandidatesForAi());
        Button aiReset=button("CLEAR CONVERSATION",PANEL);page.addView(aiReset);
        loadAiConversation();
        aiSaveButton.setOnClickListener(v->saveAiSettingsAndTest());
        aiClear.setOnClickListener(v->new android.app.AlertDialog.Builder(this)
            .setTitle("Disconnect AI?").setMessage("This removes the saved API key and provider settings from this device.")
            .setNegativeButton("CANCEL",null).setPositiveButton("DISCONNECT",(d,w)->{
                AiAssistant.clearConfig(this);aiStatusView.setText("AI NOT CONFIGURED");aiKeyInput.setText("");
                aiEndpointInput.setText(AiAssistant.defaultEndpoint());aiModelInput.setText(AiAssistant.defaultModel());
            }).show());
        aiSendButton.setOnClickListener(v->sendAiMessage());
        aiReset.setOnClickListener(v->{aiMessages=new JSONArray();saveAiConversation();renderAiConversation();});

        section(page, "09  ACTIVITY LOG", "Recent connection and operation results.");
        logView = text("Ready.", 11, MUTED, false);
        logView.setTypeface(Typeface.MONOSPACE);
        page.addView(logView);
    }

    private TextView logView;
    private EditText aiEndpointInput, aiModelInput, aiKeyInput, aiPromptInput;
    private TextView aiChatView, aiStatusView;
    private Button aiSaveButton, aiSendButton;
    private final ExecutorService aiWorker = Executors.newSingleThreadExecutor();
    private JSONArray aiMessages = new JSONArray();

    private static final String AI_SYSTEM_PROMPT =
        "You are the PS5 Debug Studio in-app game memory research assistant. Help the user design cheats through a careful, interactive experiment. " +
        "Never invent addresses, scan results, game values, screen observations, or successful tests. You cannot directly see the PS5 screen or control the console unless the app explicitly supplies such data. " +
        "Ask the user to report the exact visible value after each gameplay action, then explain which TurboScan comparison and value type to use. " +
        "Prefer small, reversible experiments. Explain how to refine candidates using changed/unchanged/increased/decreased scans. " +
        "Do not recommend memory writes, freezes, hooks, or remote function calls until a candidate is supported by repeated observations; always ask the user to confirm before any write or activation. " +
        "When uncertain, say so and request the next observation. Respond in the user's language, preferably concise Hinglish. " +
        "When enough information exists for a read-only TurboScan, append one compact JSON object with keys explanation and action. action must contain type=turbo_scan, value_type (0-9 in app selector order), comparison (0-11 in app selector order), and value as a string. " +
        "Only propose a scan after the user reports a concrete observed value and game action; otherwise ask for that observation. " +
        "The app can perform read_memory and write_memory actions only for an address in the currently loaded TurboScan results page. Use JSON action={type:read_memory,addr,length} for a read (length 1-256, normally match the candidate byte length). " +
        "A write_memory action must be based on a candidate address and byte length shown in the latest shared TurboScan results, and the user must have explicitly stated the desired replacement bytes. Use action={type:write_memory,addr,value} where value is complete hex byte pairs with no more than 256 bytes. Never guess an address or replacement bytes. The app will read live bytes, show a before/after preview, require explicit approval, re-check that bytes have not changed, write once, verify read-back and attempt rollback on failure. " +
        "When the user explicitly asks to create a cheat and supplies verified address(es) and patch byte(s), append one JSON object with explanation and action={type:create_cheat,name,mode,steps:[{op:patch,addr,type:hex,value}]}. Use only addresses supplied by the user or actual app scan results; never invent addresses or bytes. Limit to 32 patch steps and 4096 bytes per step. The app validates and asks approval to save a disabled draft; this does not apply it to memory.";

    private void loadAiConversation(){
        try{
            String raw=getSharedPreferences("ai_assistant_chat",0).getString("messages","[]");
            aiMessages=new JSONArray(raw);
        }catch(Exception e){aiMessages=new JSONArray();}
        renderAiConversation();
    }
    private void saveAiConversation(){
        getSharedPreferences("ai_assistant_chat",0).edit().putString("messages",aiMessages.toString()).apply();
    }
    private void renderAiConversation(){
        if(aiChatView==null)return;
        StringBuilder out=new StringBuilder();
        for(int i=0;i<aiMessages.length();i++){
            JSONObject m=aiMessages.optJSONObject(i);if(m==null||"system".equals(m.optString("role")))continue;
            out.append("\n").append("user".equals(m.optString("role"))?"YOU":"AI").append(":\n")
                .append(m.optString("content")).append("\n");
        }
        if(out.length()==0)out.append("AI conversation will appear here. Configure a provider to begin.");
        aiChatView.setText(out.toString());
    }
    private void saveAiSettingsAndTest(){
        try{
            String key=aiKeyInput.getText().toString().trim();
            if(!AiAssistant.isConfigured(this)&&key.isEmpty())throw new IllegalArgumentException("Enter an API key to configure AI.");
            AiAssistant.saveConfig(this,aiEndpointInput.getText().toString(),aiModelInput.getText().toString(),key);
            aiKeyInput.setText("");
            aiStatusView.setText("SETTINGS SAVED  •  API key encrypted with Android Keystore. Testing connection…");
            aiWorker.execute(()->{
                try{
                    JSONArray test=new JSONArray().put(new JSONObject().put("role","system").put("content","Reply with a short connection confirmation."));
                    test.put(new JSONObject().put("role","user").put("content","Connection test. Reply: PS5 Debug Studio AI connected."));
                    String answer=AiAssistant.chat(this,test);
                    main.post(()->{aiStatusView.setText("CONNECTED  •  "+AiAssistant.model(this)+"  •  key saved securely on this device.");toast("AI connection successful.");});
                }catch(Exception e){main.post(()->{aiStatusView.setText("SAVED, BUT CONNECTION TEST FAILED: "+e.getMessage());toast("AI test failed: "+e.getMessage());});}
            });
        }catch(Exception e){toast("Cannot save AI settings: "+e.getMessage());}
    }
    private String aiRuntimeContext(){
        StringBuilder c=new StringBuilder("App context: PS5 connected=").append(connected);
        if(connected)c.append(", selected PID=").append(selectedPid);
        c.append(". TurboScan controls available in the app: first scan, next scan, exact/bigger/smaller/between/increased/decreased/changed/unchanged comparisons, result paging and individual result editing. ");
        if(gameFile!=null)c.append(" Loaded game project: ").append(gameFile.optString("game")).append(", title ID: ").append(gameFile.optString("title_id")).append(", existing cheats: ").append(gameFile.optJSONArray("cheats")==null?0:gameFile.optJSONArray("cheats").length()).append(".");
        c.append(" Screen capture is not currently connected; only use user-reported observations or actual app scan data.");
        return c.toString();
    }
    private void appendAiMessage(String role,String content){
        try{aiMessages.put(new JSONObject().put("role",role).put("content",content));}
        catch(JSONException e){log("Could not append AI conversation message: "+e.getMessage());}
    }
    private void sendAiMessage(){
        String prompt=aiPromptInput.getText().toString().trim();
        if(prompt.isEmpty())return;
        if(!AiAssistant.isConfigured(this)){toast("Configure AI in this section first.");return;}
        if(aiMessages.length()==0)appendAiMessage("system",AI_SYSTEM_PROMPT);
        try{
            JSONObject user=new JSONObject();user.put("role","user");user.put("content",prompt+"\n\n[Current app context: "+aiRuntimeContext()+"]");
            aiMessages.put(user);saveAiConversation();renderAiConversation();aiPromptInput.setText("");
            aiSendButton.setEnabled(false);aiStatusView.setText("AI THINKING…");
            JSONArray request=new JSONArray();
            for(int i=0;i<aiMessages.length();i++)request.put(aiMessages.getJSONObject(i));
            aiWorker.execute(()->{
                try{
                    String answer=AiAssistant.chat(this,request);
                    main.post(()->{
                        AiMemoryActionProtocol.Proposal memoryProposal=AiMemoryActionProtocol.parse(answer);
                        JSONObject cheatDraft=AiCheatDraftProtocol.parse(answer);
                        AiActionProtocol.Proposal proposal=AiActionProtocol.parse(answer);
                        if(memoryProposal!=null){
                            appendAiMessage("assistant",memoryProposal.explanation+"\\n\\nPROPOSED MEMORY ACTION: "+memoryProposal.summary());
                            showAiMemoryActionApproval(memoryProposal);
                        }else if(cheatDraft!=null){
                            appendAiMessage("assistant",cheatDraft.optString("explanation","AI prepared a cheat draft.")+"\\n\\nPROPOSED CHEAT: "+cheatDraft.optString("name","Unnamed"));
                            showAiCheatDraftApproval(cheatDraft);
                        }else if(proposal!=null){
                            appendAiMessage("assistant",proposal.explanation+"\\n\\nPROPOSED APP ACTION: "+proposal.summary());
                            showAiScanApproval(proposal);
                        }else appendAiMessage("assistant",answer);
                        while(aiMessages.length()>41)aiMessages.remove(1);
                        saveAiConversation();renderAiConversation();aiSendButton.setEnabled(true);
                        aiStatusView.setText("CONNECTED  •  conversation saved locally.");
                    });
                }catch(Exception e){
                    main.post(()->{
                        appendAiMessage("assistant","AI request failed: "+e.getMessage());
                        saveAiConversation();renderAiConversation();aiSendButton.setEnabled(true);
                        aiStatusView.setText("AI REQUEST FAILED  •  check endpoint, model, key and network.");
                    });
                }
            });
        }catch(Exception e){toast("Cannot send AI message: "+e.getMessage());aiSendButton.setEnabled(true);}
    }

    private void showAiCheatDraftApproval(JSONObject draft){
        if(gameFile==null){toast("Create or import the target game's cheat file first.");return;}
        if(!enabledCheats.isEmpty()){toast("Restore active cheats before adding an AI draft.");return;}
        JSONArray steps=draft.optJSONArray("steps");
        StringBuilder preview=new StringBuilder("Name: ").append(draft.optString("name","AI Cheat"))
            .append("\\nMode: ").append(draft.optString("mode","toggle"))
            .append("\\nSteps: ").append(steps==null?0:steps.length()).append("\\n\\n");
        if(steps!=null)for(int i=0;i<steps.length();i++){
            JSONObject step=steps.optJSONObject(i);
            if(step!=null)preview.append(i+1).append(". ").append(step.optString("addr"))
                .append(" → ").append(step.optString("value")).append("\\n");
        }
        preview.append("\\nThis saves a disabled draft only. It does NOT write to PS5 memory.");
        new android.app.AlertDialog.Builder(this).setTitle("Review AI-generated cheat")
            .setMessage(preview.toString()).setNegativeButton("DISCARD",null)
            .setPositiveButton("ADD TO CHEAT LIBRARY",(d,w)->{
                try{
                    JSONObject validated=AiCheatDraftProtocol.validate(draft);
                    JSONArray cheats=gameFile.optJSONArray("cheats");
                    if(cheats==null){cheats=new JSONArray();gameFile.put("cheats",cheats);}
                    cheats.put(validated);persistGameFile();refreshCheatList();
                    log("AI cheat draft saved locally; no memory write was performed.");
                    toast("AI draft added disabled. Review before applying.");
                }catch(Exception e){toast("AI draft rejected: "+e.getMessage());}
            }).show();
    }

    private void showAiMemoryActionApproval(AiMemoryActionProtocol.Proposal proposal){
        if(!connected||client==null){toast("Connect to the PS5 before approving a memory action.");return;}
        PS5Client.TurboResult candidate=null;
        for(PS5Client.TurboResult row:lastTurboResults)if(String.format(Locale.US,"0x%016X",row.address).equalsIgnoreCase(proposal.address)||("0x"+Long.toHexString(row.address)).equalsIgnoreCase(proposal.address)){candidate=row;break;}
        if(candidate==null){toast("AI memory actions are limited to addresses on the currently loaded TurboScan page. Share the latest results again.");return;}
        final long address=candidate.address;
        final int length="read_memory".equals(proposal.type)?proposal.length:proposal.bytes.length;
        if(length<1||length>256||length>candidate.current.length){toast("Requested memory length is outside the loaded candidate size.");return;}
        if("write_memory".equals(proposal.type)&&length!=candidate.current.length){toast("Write length must exactly match the loaded candidate size.");return;}
        if("read_memory".equals(proposal.type)){
            new android.app.AlertDialog.Builder(this).setTitle("Approve AI memory read")
                .setMessage(String.format(Locale.US,"Read %d byte(s) from candidate 0x%016X?\\n\\nRead-only. No memory will be changed.",length,address))
                .setNegativeButton("CANCEL",null).setPositiveButton("READ MEMORY",(d,w)->runAsync("AI-approved read-only memory read",()->{
                    byte[] bytes=client.read(currentPid(),address,length);
                    return "AI memory read at 0x"+Long.toHexString(address).toUpperCase(Locale.US)+" ("+bytes.length+" bytes): "+toHex(bytes);
                },result->{appendAiMessage("assistant",result);saveAiConversation();renderAiConversation();memoryResult.setText(result);}))
                .show();
            return;
        }
        new android.app.AlertDialog.Builder(this).setTitle("Review AI memory write")
            .setMessage(String.format(Locale.US,"Read live bytes before preparing a write at 0x%016X…",address))
            .setNegativeButton("CANCEL",null).setPositiveButton("READ + REVIEW",(d,w)->runAsync("Reading live bytes for AI write review",()->{
                byte[] before=client.read(currentPid(),address,length);
                return new AiWriteReview(address,before,proposal.bytes);
            },review->{
                String message="Address: 0x"+String.format(Locale.US,"%016X",review.address)
                    +"\\nCurrent live bytes: "+toHex(review.before)
                    +"\\nProposed bytes: "+toHex(review.after)
                    +"\\n\\nThis changes PS5 process memory. Continue only if this is the intended value. The app will abort if the live bytes change before the write.";
                new android.app.AlertDialog.Builder(this).setTitle("Confirm PS5 memory write")
                    .setMessage(message).setNegativeButton("CANCEL",null)
                    .setPositiveButton("WRITE + VERIFY",(d2,w2)->runAsync("Applying AI-approved memory write with rollback",()->{
                        int p=currentPid();
                        byte[] latest=client.read(p,review.address,review.before.length);
                        if(!Arrays.equals(latest,review.before))throw new IOException("Memory changed after review. Write cancelled; share fresh scan results and retry.");
                        try{
                            client.write(p,review.address,review.after);
                            byte[] actual=client.read(p,review.address,review.after.length);
                            if(!Arrays.equals(actual,review.after))throw new IOException("Write read-back mismatch.");
                            return "AI-approved write verified at 0x"+Long.toHexString(review.address).toUpperCase(Locale.US)+".\\nBefore: "+toHex(review.before)+"\\nAfter: "+toHex(actual);
                        }catch(Exception failure){
                            try{
                                client.write(p,review.address,review.before);
                                byte[] restored=client.read(p,review.address,review.before.length);
                                if(!Arrays.equals(restored,review.before))throw new IOException("Rollback read-back mismatch.");
                            }catch(Exception rollback){throw new IOException("Write failed and rollback could not be verified: "+rollback.getMessage(),failure);}
                            throw new IOException("Write failed; original bytes restored and verified: "+failure.getMessage(),failure);
                        }
                    },result->{appendAiMessage("assistant",result);saveAiConversation();renderAiConversation();memoryResult.setText(result);}))
                    .show();
            }))
            .show();
    }

    private static final class AiWriteReview{
        final long address;final byte[] before,after;
        AiWriteReview(long a,byte[] b,byte[] n){address=a;before=b.clone();after=n.clone();}
    }

    private void showAiScanApproval(AiActionProtocol.Proposal proposal){
        if(!connected){toast("Connect to the PS5 before approving this scan.");return;}
        new android.app.AlertDialog.Builder(this).setTitle("Review AI scan action")
            .setMessage(proposal.summary()+"\\n\\nRead-only scan only. No memory will be written.")
            .setNegativeButton("CANCEL",null).setPositiveButton("APPROVE & RUN",(d,w)->{
                if(proposal.valueType<0||proposal.valueType>=turboType.getCount()||proposal.compare<0||proposal.compare>=turboCompare.getCount()){
                    toast("Unsupported AI scan settings.");return;
                }
                turboType.setSelection(proposal.valueType);
                turboCompare.setSelection(proposal.compare);
                turboValue.setText(proposal.value);
                if(turboActive&&scanClient!=null)turboNextScan();else turboFirstScan();
            }).show();
    }

    private void runAiApprovedTurboScan(){
        if(!connected){toast("Connect to the PS5 first.");return;}
        boolean refining=turboActive&&scanClient!=null;
        String type=String.valueOf(turboType.getSelectedItem());
        String compare=String.valueOf(turboCompare.getSelectedItem());
        String value=turboValue.getText().toString().trim();
        String title=refining?"Approve TurboScan refinement?":"Approve TurboScan first scan?";
        String detail=(refining?"This will refine the active scan.":"This will start a new memory scan.")
            +"\n\nType: "+type+"\nComparison: "+compare+"\nValue: "+(value.isEmpty()?"(not required / unknown)":value)
            +"\n\nOnly continue if these settings match the AI's latest instructions.";
        new android.app.AlertDialog.Builder(this).setTitle(title).setMessage(detail)
            .setNegativeButton("CANCEL",null).setPositiveButton("RUN SCAN",(d,w)->{
                if(refining)turboNextScan();else turboFirstScan();
            }).show();
    }
    private void verifyTurboCandidatesForAi(){
        if(!connected||client==null){toast("Connect to the PS5 first.");return;}
        if(lastTurboResults.isEmpty()){toast("Load a TurboScan results page first.");return;}
        final List<PS5Client.TurboResult> snapshot=new ArrayList<>(lastTurboResults);
        final int limit=Math.min(snapshot.size(),40);
        new android.app.AlertDialog.Builder(this).setTitle("Verify loaded candidates?")
            .setMessage("Read live memory for "+limit+" loaded candidate(s) and compare it with the last TurboScan values.\\n\\nRead-only: no memory will be changed. This checks whether values still match; it does not prove which game mechanic they control.")
            .setNegativeButton("CANCEL",null).setPositiveButton("VERIFY READ-ONLY",(d,w)->runAsync("Verifying loaded TurboScan candidates",()->{
                int pid=currentPid(),same=0,changed=0,failed=0;
                StringBuilder report=new StringBuilder("READ-ONLY CANDIDATE VERIFICATION\\n")
                    .append("PID: ").append(pid).append(" | candidates: ").append(limit).append("\\n")
                    .append("Address | scan bytes | live bytes | status\\n");
                for(int i=0;i<limit;i++){
                    PS5Client.TurboResult item=snapshot.get(i);
                    String address=String.format(Locale.US,"0x%016X",item.address);
                    try{
                        byte[] live=client.read(pid,item.address,item.current.length);
                        boolean match=Arrays.equals(live,item.current);
                        if(match)same++;else changed++;
                        report.append(address).append(" | ").append(toHex(item.current)).append(" | ").append(toHex(live))
                            .append(" | ").append(match?"MATCH":"CHANGED").append("\\n");
                        item.current=live;
                    }catch(Exception ex){
                        failed++;
                        report.append(address).append(" | ").append(toHex(item.current)).append(" | — | READ ERROR: ")
                            .append(ex.getMessage()==null?"unknown":ex.getMessage()).append("\\n");
                    }
                }
                report.append("\\nSummary: ").append(same).append(" match, ").append(changed).append(" changed, ").append(failed).append(" read errors.")
                    .append("\\nInterpretation: a match only means the bytes still equal the scan snapshot. Repeat controlled in-game changes before treating any address as a cheat candidate.");
                return report.toString();
            },report->{
                turboResult.setText(report);renderTurboRows();
                appendAiMessage("user","Please analyse this actual read-only live verification report. Explain which candidates need another controlled scan. Do not call a matching value verified as a cheat address.\\n\\n"+report);
                saveAiConversation();renderAiConversation();
                aiPromptInput.setText("Analyse the latest read-only candidate verification report and tell me the next safe in-game experiment.");
                toast("Candidate verification complete. Report ready to share with AI.");
            })).show();
    }

    private void shareTurboResultsWithAi(){
        if(!AiAssistant.isConfigured(this)){toast("Configure AI first.");return;}
        if(lastTurboResults.isEmpty()){toast("Load a TurboScan results page first.");return;}
        StringBuilder prompt=new StringBuilder("Analyse these real TurboScan results and tell me the safest next experiment. Do not assume any candidate is verified. Total matches: ")
            .append(turboResultTotal).append(". Type: ").append(turboType.getSelectedItem())
            .append(". Current comparison: ").append(turboCompare.getSelectedItem())
            .append(". Search value: ").append(turboValue.getText().toString()).append(".\nLoaded candidates (address, current bytes, previous bytes):\n");
        int limit=Math.min(lastTurboResults.size(),40);
        for(int i=0;i<limit;i++){
            PS5Client.TurboResult r=lastTurboResults.get(i);
            prompt.append(String.format(Locale.US,"0x%016X | %s | %s\n",r.address,toHex(r.current),toHex(r.previous)));
        }
        if(lastTurboResults.size()>limit)prompt.append("… ").append(lastTurboResults.size()-limit).append(" more results omitted from this message.");
        aiPromptInput.setText(prompt.toString());
        sendAiMessage();
    }

    private void startKernelLog() {
        final String ip=host.getText().toString().trim();
        if(ip.isEmpty()){toast("Enter the PS5 IP address first.");return;}
        if(kernelLogRunning)return;
        kernelLogRunning=true;
        kernelLogButton.setText("STOP KERNEL LOG");
        kernelLogView.setText("Connecting to "+ip+":3232…");
        kernelLogWorker.execute(()->{
            Socket sock=new Socket();kernelLogSocket=sock;
            try {
                sock.connect(new InetSocketAddress(ip,3232),5000);
                sock.setSoTimeout(1000);
                InputStream input=sock.getInputStream();byte[] buffer=new byte[4096];
                main.post(()->kernelLogView.setText("Connected to kernel log stream "+ip+":3232\n"));
                while(kernelLogRunning&&!sock.isClosed()){
                    int n;
                    try{n=input.read(buffer);}catch(SocketTimeoutException timeout){continue;}
                    if(n<0)break;
                    if(n>0){String chunk=new String(buffer,0,n,StandardCharsets.UTF_8);main.post(()->appendKernelLog(chunk));}
                }
                if(kernelLogRunning)main.post(()->appendKernelLog("\n[Kernel log stream closed by console.]\n"));
            }catch(Exception e){
                if(kernelLogRunning)main.post(()->appendKernelLog("\n[Kernel log error: "+e.getMessage()+"]\n"));
            }finally{
                kernelLogRunning=false;kernelLogSocket=null;
                try{sock.close();}catch(Exception ignored){}
                main.post(()->{if(kernelLogButton!=null)kernelLogButton.setText("START KERNEL LOG (TCP 3232)");});
            }
        });
    }
    private void appendKernelLog(String chunk){
        if(kernelLogView==null)return;
        String current=kernelLogView.getText().toString()+chunk;
        if(current.length()>24000)current=current.substring(current.length()-24000);
        kernelLogView.setText(current);
    }
    private void stopKernelLog(){
        kernelLogRunning=false;
        Socket sock=kernelLogSocket;kernelLogSocket=null;
        if(sock!=null)try{sock.close();}catch(Exception ignored){}
        if(kernelLogButton!=null)kernelLogButton.setText("START KERNEL LOG (TCP 3232)");
        if(kernelLogView!=null)appendKernelLog("\n[Kernel log stopped.]\n");
    }

    private void section(LinearLayout parent, String heading, String detail) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(13), dp(12), dp(13), dp(10));
        box.setBackgroundColor(PANEL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(16);
        lp.bottomMargin = dp(8);
        parent.addView(box, lp);
        TextView h = text(heading, 14, TEXT, true);
        box.addView(h);
        TextView d = text(detail, 12, MUTED, false);
        d.setPadding(0, dp(5), 0, 0);
        box.addView(d);
    }

    private EditText field(String hint, String value) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setText(value);
        e.setHint(hint);
        e.setHintTextColor(MUTED);
        e.setTextColor(TEXT);
        e.setTextSize(16);
        e.setPadding(dp(12), dp(5), dp(12), dp(5));
        e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.topMargin = dp(5);
        return addFieldLater(e, lp);
    }

    private EditText addFieldLater(EditText e, LinearLayout.LayoutParams lp) {
        e.setLayoutParams(lp);
        return e;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(Math.max(size, 13));
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(0, dp(3), 0, dp(3));
        return t;
    }

    private Button button(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(13);
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        return b;
    }

    private void log(String message) {
        String stamp = new java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        main.post(() -> {
            String old = logView == null ? "" : logView.getText().toString();
            String next = "[" + stamp + "] " + message + "\n" + old;
            if (next.length() > 6000) next = next.substring(0, 6000);
            if (logView != null) logView.setText(next);
        });
    }

    private <T> void runAsync(String action, Callable<T> task, Consumer<T> onSuccess) {
        log(action + "…");
        worker.execute(() -> {
            try {
                T result = task.call();
                main.post(() -> onSuccess.accept(result));
            } catch (Exception ex) {
                String message = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                boolean connectionLost=client!=null&&!client.isConnected();
                if(connectionLost){
                    try{client.close();}catch(Exception ignored){}
                    client=null;connected=false;
                    if(scanClient!=null)try{scanClient.close();}catch(Exception ignored){}
                    scanClient=null;turboActive=false;
                    addressAutoRefresh=false;addressHandler.removeCallbacks(addressRefreshTick);
                    addressHandler.removeCallbacks(addressFreezeTick);
                }
                main.post(() -> {
                    if(connectionLost){
                        if(connectionStatus!=null){connectionStatus.setText("●  DISCONNECTED — reconnect required");connectionStatus.setTextColor(MUTED);}
                        if(connectButton!=null)connectButton.setText("CONNECT TO PS5");
                        if(addressAutoButton!=null)addressAutoButton.setText("AUTO REFRESH: OFF");
                        log("Console connection lost; reconnect before retrying.");
                    }
                    log(action + " failed: " + message);
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void discoverConsole() {
        runAsync("Searching local network for ps5debug", () -> {
            DatagramSocket udp = new DatagramSocket();
            try {
                udp.setBroadcast(true);
                udp.setSoTimeout(700);
                byte[] probe = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0xFFFFAAAA).array();
                DatagramPacket request = new DatagramPacket(probe, probe.length,
                        InetAddress.getByName("255.255.255.255"), 1010);
                for (int attempt = 0; attempt < 3; attempt++) {
                    udp.send(request);
                    byte[] response = new byte[16];
                    DatagramPacket packet = new DatagramPacket(response, response.length);
                    try {
                        udp.receive(packet);
                        if (packet.getLength() == 4 &&
                                Arrays.equals(Arrays.copyOf(packet.getData(), 4), probe)) {
                            return packet.getAddress().getHostAddress();
                        }
                    } catch (SocketTimeoutException ignored) { }
                }
                throw new IOException("No ps5debug response found. Check that the PS5 and phone are on the same LAN and UDP 1010 is reachable.");
            } finally {
                udp.close();
            }
        }, address -> {
            host.setText(address);
            log("Found PS5 debug server at " + address + ". Connect when ready.");
            Toast.makeText(this, "PS5 found: " + address, Toast.LENGTH_LONG).show();
        });
    }

    private void connectOrDisconnect() {
        if (connected) {
            worker.execute(() -> {
                if(client!=null){
                    if(allocatedBufferAddress!=0&&allocatedBufferLength>0&&allocatedBufferPid>0){
                        try{client.free(allocatedBufferPid,allocatedBufferAddress,allocatedBufferLength);log("Freed session-owned remote buffer before disconnect.");}
                        catch(Exception cleanup){log("Remote buffer cleanup warning: "+cleanup.getMessage());}
                    }
                    try{client.releaseTrampolinePools();}catch(Exception cleanup){log("Trampoline cleanup warning: "+cleanup.getMessage());}
                    client.close();
                }
                if (scanClient != null) scanClient.close();
                scanClient = null; turboActive = false;
                client = null;
                connected = false;
                allocatedBufferAddress=0;allocatedBufferLength=0;allocatedBufferPid=-1;
                main.post(() -> {
                    connectionStatus.setText("●  DISCONNECTED");
                    connectionStatus.setTextColor(MUTED);
                    connectButton.setText("CONNECT TO PS5");
                });
                log("Disconnected.");
            });
            return;
        }
        String ip = host.getText().toString().trim();
        int p;
        try { p = Integer.parseInt(port.getText().toString().trim()); }
        catch (Exception e) { Toast.makeText(this, "Enter a valid port.", Toast.LENGTH_SHORT).show(); return; }
        final int portValue = p;
        runAsync("Connecting", () -> {
            PS5Client c = new PS5Client();
            String version = c.connect(ip, portValue);
            client = c;
            connected = true;
            return "Connected. ps5debug version: " + version;
        }, result -> {
            connectionStatus.setText("●  CONNECTED  •  " + host.getText().toString().trim());
            connectionStatus.setTextColor(GREEN);
            connectButton.setText("DISCONNECT");
            log(result);
            if(hasFrozenAddresses()){addressHandler.removeCallbacks(addressFreezeTick);addressHandler.post(addressFreezeTick);}
            refreshProcesses();
        });
    }

    private void queryConsoleInfo() {
        if(!connected){toast("Connect to the PS5 first.");return;}
        runAsync("Querying payload and firmware information",()->client.consoleInfo(),consoleInfo::setText);
    }

    private void refreshProcesses() {
        if (!connected) { toast("Connect to the PS5 first."); return; }
        runAsync("Loading process list", () -> {
            List<ProcessEntry> list = client.listProcesses();
            processes = list;
            List<String> names = new ArrayList<>();
            for (ProcessEntry p : list) names.add(p.name + "  (PID " + p.pid + ")");
            return new JSONArray(names).toString();
        }, json -> {
            List<String> names = new ArrayList<>();
            for (ProcessEntry p : processes) names.add(p.name + "  (PID " + p.pid + ")");
            if (names.isEmpty()) names.add("No processes found");
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
            processSpinner.setAdapter(adapter);
            if (!processes.isEmpty()) {
                selectedPid = processes.get(0).pid;
                pid.setText(String.valueOf(selectedPid));
            }
            log("Loaded " + processes.size() + " process(es).");
        });
    }

    private int currentPid() {
        String value = pid.getText().toString().trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Select a process first.");
        int p = Integer.decode(value);
        if (p <= 0) throw new IllegalArgumentException("Invalid PID.");
        selectedPid = p;
        return p;
    }

    private void showProcessInfo() {
        if (!connected) { toast("Connect to the PS5 first."); return; }
        runAsync("Reading process details", () -> {
            ProcInfo pi = client.procInfo(currentPid());
            return "PID: " + pi.pid + "\nName: " + pi.name + "\nPath: " + pi.path +
                    "\nTitle ID: " + pi.titleId + "\nContent ID: " + pi.contentId;
        }, value -> processInfo.setText(value));
    }

    private void readMemory() {
        if (!connected) { toast("Connect to the PS5 first."); return; }
        runAsync("Reading memory", () -> {
            int p = currentPid();
            long address = resolveAddress(readAddress.getText().toString().trim(), p);
            int length = Integer.decode(readLength.getText().toString().trim());
            if (length < 1 || length > 4096) throw new IllegalArgumentException("Read length must be 1–4096 bytes.");
            return new HexPage(address, client.read(p, address, length));
        }, page -> {
            hexEditorBase = page.address;
            hexEditorOriginal = page.data.clone();
            hexEditorInput.setText(toHex(page.data));
            hexEditorInfo.setText(String.format(Locale.US, "Loaded %d bytes at 0x%X  •  Edit hex bytes, then WRITE + VERIFY.", page.data.length, page.address));
            memoryResult.setText("0x" + Long.toHexString(page.address).toUpperCase(Locale.US) + "\n" + hexDump(page.data, page.address));
        });
    }

    private void inspectHexTypedValue() {
        if (hexEditorOriginal.length == 0) { toast("Load a memory page first."); return; }
        try {
            byte[] data = parseHex(hexEditorInput.getText().toString());
            int offset = Integer.parseInt(hexTypedOffset.getText().toString().trim());
            if (offset < 0 || offset >= data.length) throw new IllegalArgumentException("Offset must be inside the loaded page.");
            int type = hexTypedType.getSelectedItemPosition();
            if (type == 10 || type == 11) {
                byte[] tail = Arrays.copyOfRange(data, offset, data.length);
                int end=0;
                if(type==10){while(end<tail.length&&tail[end]!=0)end++;}
                else {while(end+1<tail.length&&!(tail[end]==0&&tail[end+1]==0))end+=2;if((end&1)!=0)end--;}
                String value = new String(tail, 0, end, type==10?StandardCharsets.UTF_8:StandardCharsets.UTF_16LE);
                hexTypedResult.setText(String.format(Locale.US, "Address: 0x%X\\nOffset: %d\\n%s: %s\\nBytes: %d", hexEditorBase + offset, offset, type==10?"UTF-8":"UTF-16LE", value, end));
                return;
            }
            int size = type <= 1 ? 1 : type <= 3 ? 2 : (type <= 5 || type == 8) ? 4 : 8;
            if (offset + size > data.length) throw new IllegalArgumentException("Not enough bytes remain for this type.");
            ByteBuffer b = ByteBuffer.wrap(data, offset, size).order(ByteOrder.LITTLE_ENDIAN);
            String value;
            switch(type) {
                case 0: value = Integer.toString(data[offset] & 255); break;
                case 1: value = Byte.toString(data[offset]); break;
                case 2: value = Integer.toString(b.getShort() & 65535); break;
                case 3: value = Short.toString(b.getShort()); break;
                case 4: value = Long.toString(b.getInt() & 0xFFFFFFFFL); break;
                case 5: value = Integer.toString(b.getInt()); break;
                case 6: value = Long.toUnsignedString(b.getLong()); break;
                case 7: value = Long.toString(b.getLong()); break;
                case 8: value = Float.toString(b.getFloat()); break;
                case 9: value = Double.toString(b.getDouble()); break;
                default: throw new IllegalArgumentException("Unsupported type.");
            }
            hexTypedResult.setText(String.format(Locale.US, "Address: 0x%X\\nOffset: %d\\nType: %s\\nValue: %s\\nRaw: %s", hexEditorBase + offset, offset, hexTypedType.getSelectedItem(), value, toHex(Arrays.copyOfRange(data, offset, offset + size))));
        } catch (Exception e) { hexTypedResult.setText("Cannot inspect value: " + e.getMessage()); }
    }

    private static final class HexPage {
        final long address; final byte[] data;
        HexPage(long address, byte[] data) { this.address = address; this.data = data; }
    }

    private void moveHexEditorPage(int direction) {
        try {
            int length = Integer.decode(readLength.getText().toString().trim());
            if (length < 1 || length > 4096) throw new IllegalArgumentException("Page size must be 1–4096 bytes.");
            long base = resolveAddress(readAddress.getText().toString().trim(), currentPid());
            long next = Math.addExact(base, (long)direction * length);
            if (next < 0) throw new IllegalArgumentException("Address is outside the supported range.");
            readAddress.setText("0x" + Long.toHexString(next).toUpperCase(Locale.US));
            readMemory();
        } catch (Exception e) { toast("Cannot change page: " + e.getMessage()); }
    }

    private void importHexPatchFile(){
        if(hexEditorOriginal.length==0){toast("Load a memory page first so the patch length can be checked.");return;}
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/octet-stream");
        startActivityForResult(intent,REQ_IMPORT_HEX_PATCH);
    }
    private void exportHexEditorPage(){
        if(hexEditorInput==null||hexEditorOriginal.length==0){toast("Load a hex page first.");return;}
        try{
            byte[] bytes=parseHex(hexEditorInput.getText().toString());
            if(bytes.length==0||bytes.length>4096)throw new IllegalArgumentException("Export page must contain 1–4096 bytes.");
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/octet-stream");
            intent.putExtra(Intent.EXTRA_TITLE,String.format(Locale.US,"PS5-0x%016X.bin",hexEditorBase));
            startActivityForResult(intent,REQ_EXPORT_HEX_PAGE);
        }catch(Exception e){toast("Cannot export page: "+e.getMessage());}
    }

    private void writeHexEditorPage(boolean restoreOriginal) {
        if (!connected) { toast("Connect to the PS5 first."); return; }
        if (hexEditorOriginal.length == 0) { toast("Load a memory page first."); return; }
        final byte[] data;
        try {
            data = restoreOriginal ? hexEditorOriginal.clone() : parseHex(hexEditorInput.getText().toString());
            if (data.length == 0 || data.length > 4096) throw new IllegalArgumentException("Write 1–4096 bytes.");
            if (restoreOriginal && data.length != hexEditorOriginal.length) throw new IllegalArgumentException("Original page snapshot is invalid.");
        } catch (Exception e) { toast("Invalid hex data: " + e.getMessage()); return; }
        final long address = hexEditorBase;
        runAsync(restoreOriginal ? "Restoring original bytes" : "Writing hex page", () -> {
            int p = currentPid();
            client.write(p, address, data);
            byte[] actual = client.read(p, address, data.length);
            if (!Arrays.equals(data, actual)) throw new IOException("Read-back verification failed; memory does not match the requested bytes.");
            return new HexPage(address, actual);
        }, page -> {
            hexEditorBase = page.address;
            hexEditorOriginal = page.data.clone();
            hexEditorInput.setText(toHex(page.data));
            hexEditorInfo.setText(String.format(Locale.US, "Verified %d bytes at 0x%X  •  Snapshot updated.", page.data.length, page.address));
            memoryResult.setText("0x" + Long.toHexString(page.address).toUpperCase(Locale.US) + "\n" + hexDump(page.data, page.address));
            log((restoreOriginal ? "Restored original " : "Wrote and verified ") + page.data.length + " bytes at 0x" + Long.toHexString(page.address).toUpperCase(Locale.US));
        });
    }

    private void writeMemory() {
        if (!connected) { toast("Connect to the PS5 first."); return; }
        runAsync("Writing memory with rollback and read-back verification", () -> {
            int p = currentPid();
            long address = resolveAddress(writeAddress.getText().toString().trim(), p);
            byte[] data = parseHex(writeBytes.getText().toString());
            if (data.length == 0 || data.length > 4096) throw new IllegalArgumentException("Write 1–4096 bytes.");
            byte[] original = client.read(p, address, data.length);
            try {
                client.write(p, address, data);
                byte[] actual = client.read(p, address, data.length);
                if (!Arrays.equals(data, actual)) throw new IOException("Write read-back did not match requested bytes.");
                return "Wrote and verified " + data.length + " byte(s) at 0x" + Long.toHexString(address).toUpperCase(Locale.US);
            } catch (Exception failure) {
                try {
                    client.write(p, address, original);
                    byte[] restored = client.read(p, address, original.length);
                    if (!Arrays.equals(original, restored))
                        throw new IOException("Rollback read-back mismatch.");
                } catch (Exception rollbackFailure) {
                    throw new IOException("Memory write failed and automatic rollback could not be verified. Original error: "
                        + failure.getMessage() + "; rollback error: " + rollbackFailure.getMessage(), failure);
                }
                throw new IOException("Memory write failed; original bytes were restored and verified. " + failure.getMessage(), failure);
            }
        }, value -> {
            memoryResult.setText(value);
            log(value);
        });
    }






    private void writeAssembledCode(){
        if(!connected){toast("Connect to the PS5 first.");return;}
        if(generatedAssembly.length==0){toast("Assemble code first.");return;}
        runAsync("Writing assembled code with read-back verification",()->{
            int p=currentPid();boolean executableCave=false;
            for(MapEntry m:client.maps(p))if((m.prot&4)!=0&&Long.compareUnsigned(generatedAssemblyAddress,m.start)>=0&&Long.compareUnsigned(generatedAssemblyAddress+generatedAssembly.length,m.end)<=0){executableCave=true;break;}
            if(!executableCave)throw new IOException("Assembly destination must fit entirely inside an executable memory region.");
            byte[] before=client.read(p,generatedAssemblyAddress,generatedAssembly.length);
            for(byte v:before){int x=v&255;if(x!=0x00&&x!=0x90&&x!=0xCC)throw new IOException("Assembly destination contains non-padding bytes; refusing to overwrite existing code.");}
            client.write(p,generatedAssemblyAddress,generatedAssembly);
            byte[] after=client.read(p,generatedAssemblyAddress,generatedAssembly.length);
            if(!Arrays.equals(after,generatedAssembly)){
                client.write(p,generatedAssemblyAddress,before);
                throw new IOException("Code write verification failed; original bytes were restored.");
            }
            generatedAssemblyOriginal=before;
            return "Assembled bytes written and verified at 0x"+Long.toHexString(generatedAssemblyAddress).toUpperCase(Locale.US)+". Original bytes cached for restore.";
        },asmResult::setText);
    }
    private void restoreAssembledCode(){
        if(!connected){toast("Connect to the PS5 first.");return;}
        if(generatedAssemblyOriginal.length==0){toast("No original code snapshot available.");return;}
        runAsync("Restoring original assembled-code bytes",()->{
            int p=currentPid();client.write(p,generatedAssemblyAddress,generatedAssemblyOriginal);
            byte[] check=client.read(p,generatedAssemblyAddress,generatedAssemblyOriginal.length);
            if(!Arrays.equals(check,generatedAssemblyOriginal))throw new IOException("Original-byte restore verification failed.");
            generatedAssemblyOriginal=new byte[0];
            return "Original bytes restored and verified.";
        },asmResult::setText);
    }

    private void installGeneratedHook(){
        if(!connected){toast("Connect to the PS5 first.");return;}
        if(generatedHookPatch.length==0||generatedHookTrampoline.length==0){toast("Generate a hook first.");return;}
        final long cave;
        try{cave=resolveAddress(hookCaveAddress.getText().toString().trim(),currentPid());}
        catch(Exception e){toast("Invalid executable cave address: "+e.getMessage());return;}
        runAsync("Installing hook with verification",()->{
            int p=currentPid();
            long detour=parseAddress(hookDetour.getText().toString().trim());
            MapEntry caveRegion=null;boolean detourExecutable=false;
            for(MapEntry m:client.maps(p)){
                if((m.prot&4)!=0&&Long.compareUnsigned(cave,m.start)>=0&&Long.compareUnsigned(cave+generatedHookTrampoline.length,m.end)<=0)caveRegion=m;
                if((m.prot&4)!=0&&Long.compareUnsigned(detour,m.start)>=0&&Long.compareUnsigned(detour,m.end)<0)detourExecutable=true;
            }
            if(caveRegion==null)throw new IOException("Trampoline address is not fully inside one executable memory region.");
            if(!detourExecutable)throw new IOException("Detour address is not inside an executable memory region.");
            if(Long.compareUnsigned(detour,cave)>=0&&Long.compareUnsigned(detour,cave+generatedHookTrampoline.length)<0)throw new IOException("Detour code and trampoline cannot overlap.");
            byte[] caveBefore=client.read(p,cave,generatedHookTrampoline.length);
            for(byte v:caveBefore){int x=v&255;if(x!=0x00&&x!=0x90&&x!=0xCC)throw new IOException("Selected executable cave contains non-padding bytes; refusing to overwrite it.");}
            try{
                client.write(p,cave,generatedHookTrampoline);
                byte[] caveCheck=client.read(p,cave,generatedHookTrampoline.length);
                if(!Arrays.equals(caveCheck,generatedHookTrampoline))throw new IOException("Trampoline read-back mismatch.");
            }catch(Exception caveError){
                try{client.write(p,cave,caveBefore);byte[] restoredCave=client.read(p,cave,caveBefore.length);if(!Arrays.equals(restoredCave,caveBefore))throw new IOException("cave restore mismatch");}
                catch(Exception restoreCave){throw new IOException("Trampoline write failed and cave rollback failed: "+restoreCave.getMessage(),caveError);}
                throw new IOException("Trampoline write failed; original cave bytes restored: "+caveError.getMessage(),caveError);
            }
            generatedHookCaveOriginal=caveBefore;generatedHookCave=cave;
            try{
                client.write(p,generatedHookSite,generatedHookPatch);
                byte[] patchCheck=client.read(p,generatedHookSite,generatedHookPatch.length);
                if(!Arrays.equals(patchCheck,generatedHookPatch))throw new IOException("Hook patch read-back mismatch.");
            }catch(Exception installError){
                try{
                    client.write(p,generatedHookSite,generatedHookOriginal);
                    byte[] restored=client.read(p,generatedHookSite,generatedHookOriginal.length);
                    if(!Arrays.equals(restored,generatedHookOriginal))throw new IOException("hook-site restore verification mismatch");
                    client.write(p,cave,caveBefore);
                    byte[] restoredCave=client.read(p,cave,caveBefore.length);
                    if(!Arrays.equals(restoredCave,caveBefore))throw new IOException("cave restore verification mismatch");
                }catch(Exception restoreError){
                    throw new IOException("Hook install failed ("+installError.getMessage()+") and automatic restore failed ("+restoreError.getMessage()+"). Inspect hook-site memory immediately.",installError);
                }
                throw new IOException("Hook install failed; original bytes were restored and verified: "+installError.getMessage(),installError);
            }
            return "Trampoline written and hook patch verified.\\nCave: 0x"+Long.toHexString(cave).toUpperCase(Locale.US)+"\\nHook: 0x"+Long.toHexString(generatedHookSite).toUpperCase(Locale.US);
        },hookResult::setText);
    }
    private void restoreGeneratedHook(){
        if(!connected){toast("Connect to the PS5 first.");return;}
        if(generatedHookOriginal.length==0){toast("Generate a hook first; original bytes are not cached.");return;}
        runAsync("Restoring original hook bytes",()->{
            int p=currentPid();client.write(p,generatedHookSite,generatedHookOriginal);
            byte[] check=client.read(p,generatedHookSite,generatedHookOriginal.length);
            if(!Arrays.equals(check,generatedHookOriginal))throw new IOException("Hook-site restore read-back mismatch.");
            if(generatedHookCaveOriginal.length>0){
                client.write(p,generatedHookCave,generatedHookCaveOriginal);
                byte[] caveCheck=client.read(p,generatedHookCave,generatedHookCaveOriginal.length);
                if(!Arrays.equals(caveCheck,generatedHookCaveOriginal))throw new IOException("Hook was removed, but trampoline cave restore verification failed.");
                generatedHookCaveOriginal=new byte[0];generatedHookCave=0;
            }
            return "Original hook-site bytes restored and verified; trampoline cave padding restored too.";
        },hookResult::setText);
    }

    private void assembleMiniUi(){
        try{
            long base=parseAddress(asmAddress.getText().toString());
            byte[] code=assembleMini(asmSource.getText().toString(),base);
            generatedAssembly=code;generatedAssemblyAddress=base;generatedAssemblyOriginal=new byte[0];
            asmResult.setText("Base: 0x"+Long.toHexString(base).toUpperCase(Locale.US)+"\\nLength: "+code.length+" bytes\\nHEX: "+toHex(code));
        }catch(Exception e){asmResult.setText("Assembly error: "+e.getMessage());}
    }
    private static byte[] assembleMini(String source,long base)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        String[] lines=source.replace("\\r","").split("\\n");
        for(String original:lines){
            String line=original.split(";",2)[0].trim();if(line.isEmpty())continue;
            String low=line.toLowerCase(Locale.US);
            if(low.equals("nop")){out.write(0x90);continue;}
            if(low.equals("ret")||low.equals("retq")){out.write(0xC3);continue;}
            if(low.equals("int3")){out.write(0xCC);continue;}
            if(low.startsWith("db ")){out.write(parseHex(line.substring(3)));continue;}
            String[] p=line.split("\\s+",2);String op=p[0].toLowerCase(Locale.US);
            if((op.equals("push")||op.equals("pop"))&&p.length==2){
                int reg=x64Register(p[1].trim());if(reg<0)throw new IOException("Unsupported register: "+p[1]);
                if(reg<8)out.write((op.equals("push")?0x50:0x58)+reg);
                else{out.write(0x41);out.write((op.equals("push")?0x50:0x58)+(reg-8));}
                continue;
            }
            if(op.equals("mov")&&p.length==2){
                String[] args=p[1].split(",",2);if(args.length!=2)throw new IOException("mov syntax: mov rax, 0x1234");
                int reg=x64Register(args[0].trim());if(reg<0)throw new IOException("Only 64-bit registers are supported for mov.");
                long imm=parseAddress(args[1].trim());
                out.write(reg>=8?0x49:0x48);out.write(0xB8+(reg&7));
                for(int i=0;i<8;i++)out.write((int)(imm>>>(i*8))&255);
                continue;
            }
            if((op.equals("jmp")||op.equals("call"))&&p.length==2){
                int indirect=x64Register(p[1].trim());
                if(indirect>=0){
                    if(indirect>=8)out.write(0x41);
                    out.write(0xFF);out.write((op.equals("call")?0xD0:0xE0)+(indirect&7));
                    continue;
                }
                long target=parseAddress(p[1].trim());long ip=base+out.size();
                long rel=target-(ip+5);
                if(rel<Integer.MIN_VALUE||rel>Integer.MAX_VALUE)throw new IOException("Relative branch target is out of 32-bit range.");
                out.write(op.equals("jmp")?0xE9:0xE8);
                for(int i=0;i<4;i++)out.write((int)(rel>>>(i*8))&255);
                continue;
            }
            throw new IOException("Unsupported instruction: "+line);
        }
        if(out.size()==0)throw new IOException("No instructions assembled.");
        return out.toByteArray();
    }
    private static int x64Register(String name){
        String[] regs={"rax","rcx","rdx","rbx","rsp","rbp","rsi","rdi","r8","r9","r10","r11","r12","r13","r14","r15"};
        for(int i=0;i<regs.length;i++)if(regs[i].equalsIgnoreCase(name))return i;return -1;
    }

    private void readHookOriginal(){
        if(!connected){toast("Connect to the PS5 first.");return;}
        final long site;final int length;
        try{site=parseAddress(hookTarget.getText().toString());length=Integer.parseInt(hookLength.getText().toString().trim());if(length<14||length>64)throw new IllegalArgumentException("Length must be 14–64 bytes.");}
        catch(Exception e){toast("Hook read settings: "+e.getMessage());return;}
        runAsync("Reading original hook-site bytes",()->toHex(client.read(currentPid(),site,length)),value->{
            hookOriginal.setText(value);hookResult.setText("Original bytes captured from 0x"+Long.toHexString(site).toUpperCase(Locale.US)+". Review them, then generate the hook.");
        });
    }

    private void buildAbsoluteHook(){
        try{
            long site=parseAddress(hookTarget.getText().toString()),detour=parseAddress(hookDetour.getText().toString());
            int length=Integer.parseInt(hookLength.getText().toString().trim());
            byte[] original=parseHex(hookOriginal.getText().toString());
            if(length<14||length>64)throw new IllegalArgumentException("Stolen length must be 14–64 bytes.");
            if(original.length!=length)throw new IllegalArgumentException("Original byte count must exactly match stolen length ("+length+").");
            byte[] patch=absoluteJump(detour);if(patch.length>length)throw new IllegalArgumentException("Patch does not fit stolen length.");
            byte[] padded=Arrays.copyOf(patch,length);Arrays.fill(padded,patch.length,length,(byte)0x90);
            byte[] trampoline=new byte[length+14];System.arraycopy(original,0,trampoline,0,length);
            byte[] back=absoluteJump(site+length);System.arraycopy(back,0,trampoline,length,back.length);
            generatedHookPatch=padded;generatedHookTrampoline=trampoline;generatedHookOriginal=original.clone();generatedHookSite=site;
            hookResult.setText("HOOK SITE: 0x"+Long.toHexString(site).toUpperCase(Locale.US)+"\\nDETOUR: 0x"+Long.toHexString(detour).toUpperCase(Locale.US)
                +"\\nStolen bytes: "+length+"\\n\\nPATCH (write at hook site):\\n"+toHex(padded)
                +"\\n\\nTRAMPOLINE (place in executable cave):\\n"+toHex(trampoline)
                +"\\n\\nWARNING: supplied original bytes must end on whole instruction boundaries. RIP-relative instructions and relative branches are not relocated. Use a verified executable cave.");
        }catch(Exception e){hookResult.setText("Hook builder error: "+e.getMessage());}
    }
    private static byte[] absoluteJump(long target){
        ByteBuffer b=ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte)0xFF).put((byte)0x25).putInt(0).putLong(target);return b.array();
    }

    private static final class PointerNode { final long slot,value; PointerNode(long s,long v){slot=s;value=v;} }
    private static final class PointerPath { final long address; final List<Long> offsets; final Set<Long> visited; PointerPath(long a,List<Long> o,Set<Long> v){address=a;offsets=o;visited=v;} }
    private static final class PointerHit { final String module; final long baseOffset; final List<Long> offsets; PointerHit(String m,long b,List<Long> o){module=m;baseOffset=b;offsets=o;} }
    private void startPointerScan(){
        if(!connected){toast("Connect to the PS5 first.");return;}
        final long target,maxOff; final int depth,budget;
        try{
            target=resolveAddress(pointerTarget.getText().toString().trim(),currentPid());
            String raw=pointerOffset.getText().toString().trim();
            maxOff=raw.startsWith("0x")||raw.startsWith("0X")?Long.parseUnsignedLong(raw.substring(2),16):Long.parseLong(raw);
            depth=Integer.parseInt(pointerDepth.getText().toString().trim());
            budget=Integer.parseInt(pointerBudget.getText().toString().trim());
            if(maxOff<0||depth<1||depth>5||budget<1||budget>256)throw new IllegalArgumentException("Offset must be non-negative, depth 1–5 and budget 1–256 MiB.");
        }catch(Exception e){toast("Pointer scan settings: "+e.getMessage());return;}
        pointerCancel=false;lastPointerHits=new ArrayList<>();lastPointerPid=currentPid();lastPointerTarget=target;pointerResult.setText("Reading process maps…");
        runAsync("Building pointer map and searching chains",()->{
            int p=currentPid(); List<MapEntry> regions=client.maps(p), sources=new ArrayList<>();
            long remaining=(long)budget*1024*1024,scanned=0;
            List<MapEntry> readable=new ArrayList<>();
            for(MapEntry m:regions)if((m.prot&1)!=0&&m.end>m.start)readable.add(m);
            Collections.sort(readable,(a,b)->{
                int pa=isPointerModule(a)?0:((a.prot&2)!=0&&(a.prot&4)==0?1:2);
                int pb=isPointerModule(b)?0:((b.prot&2)!=0&&(b.prot&4)==0?1:2);
                return Integer.compare(pa,pb);
            });
            for(MapEntry m:readable){
                if(remaining<=0)break;
                long size=Math.min(m.end-m.start,remaining);
                MapEntry x=new MapEntry();x.name=m.name;x.start=m.start;x.end=m.start+size;x.prot=m.prot;
                sources.add(x);remaining-=size;
            }
            if(sources.isEmpty())throw new IOException("No readable process memory regions found.");
            Collections.sort(sources,(a,b)->Long.compareUnsigned(a.start,b.start));
            List<PointerNode> nodes=new ArrayList<>();
            for(MapEntry m:sources){
                long cursor=m.start;
                while(cursor<m.end){
                    if(pointerCancel)throw new IOException("Pointer scan cancelled.");
                    int n=(int)Math.min(65536,m.end-cursor);byte[] raw=client.read(p,cursor,n);
                    int first=(int)((8-(cursor&7))&7);ByteBuffer bb=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
                    for(int off=first;off+8<=raw.length;off+=8){
                        long value=bb.getLong(off);if(value==0)continue;
                        boolean inRange=pointerInRanges(sources,value);
                        if(inRange)nodes.add(new PointerNode(cursor+off,value));
                        if(nodes.size()>=500000)break;
                    }
                    scanned+=raw.length;cursor+=raw.length;if(nodes.size()>=500000)break;
                }
                if(nodes.size()>=500000)break;
            }
            if(nodes.isEmpty())return "Pointer map contains no pointers into scanned readable regions. Scanned "+(scanned/1048576)+" MiB.";
            Collections.sort(nodes,(a,b)->Long.compareUnsigned(a.value,b.value));
            List<PointerPath> frontier=new ArrayList<>();Set<Long> rootVisited=new HashSet<>();rootVisited.add(target);frontier.add(new PointerPath(target,new ArrayList<>(),rootVisited));
            List<PointerHit> hits=new ArrayList<>();
            for(int level=1;level<=depth&&!frontier.isEmpty()&&hits.size()<500;level++){
                List<PointerPath> next=new ArrayList<>();
                for(PointerPath path:frontier){
                    if(pointerCancel)throw new IOException("Pointer scan cancelled.");
                    int lo=pointerLowerBound(nodes,path.address-maxOff),hi=pointerUpperBound(nodes,path.address);
                    for(int k=lo;k<hi;k++){
                        PointerNode node=nodes.get(k);if(path.visited.contains(node.slot))continue;
                        List<Long> offsets=new ArrayList<>(path.offsets);offsets.add(path.address-node.value);
                        Set<Long> chainVisited=new HashSet<>(path.visited);chainVisited.add(node.slot);
                        MapEntry base=null;
                        for(MapEntry m:regions){
                            String nm=m.name==null?"":m.name.toLowerCase(Locale.US);
                            boolean module=isPointerModule(m);
                            if(module&&Long.compareUnsigned(node.slot,m.start)>=0&&Long.compareUnsigned(node.slot,m.end)<0){base=m;break;}
                        }
                        if(base!=null){
                            long moduleBase=base.start;for(MapEntry m:regions)if(base.name.equals(m.name)&&Long.compareUnsigned(m.start,moduleBase)<0)moduleBase=m.start;
                            Collections.reverse(offsets);hits.add(new PointerHit(base.name,node.slot-moduleBase,new ArrayList<>(offsets)));
                        }else if(level<depth&&next.size()<100000)next.add(new PointerPath(node.slot,offsets,chainVisited));
                        if(hits.size()>=500)break;
                    }
                }
                frontier=next;
            }
            lastPointerHits=new ArrayList<>(hits);lastPointerPid=p;lastPointerTarget=target;
            StringBuilder out=new StringBuilder("Pointer candidates: ").append(nodes.size()).append("\nScanned: ").append(scanned/1048576).append(" MiB\nTarget: 0x").append(Long.toHexString(target).toUpperCase(Locale.US)).append("\nChains: ").append(hits.size()).append("\n\n");
            for(int i=0;i<hits.size();i++){PointerHit h=hits.get(i);out.append(i+1).append(". ").append(h.module).append("+0x").append(Long.toHexString(h.baseOffset).toUpperCase(Locale.US));for(long off:h.offsets)out.append(" -> 0x").append(Long.toHexString(off).toUpperCase(Locale.US));out.append("\n");}
            if(hits.isEmpty())out.append("No module-rooted chains found. Increase offset, depth or scan budget.");
            if(nodes.size()>=500000)out.append("\nCandidate cap reached (500,000); results may be incomplete.");
            if(hits.size()>=500)out.append("\nDisplay cap reached (500 chains).");
            return out.toString();
        },value->{pointerResult.setText(value);log("Pointer scan completed.");});
    }
    private void savePointerChains(){
        if(lastPointerHits.isEmpty()){toast("No pointer chains to save. Run a scan first.");return;}
        try{
            JSONObject root=new JSONObject();root.put("version",1);root.put("pid",lastPointerPid);
            root.put("target",Long.toUnsignedString(lastPointerTarget,16));
            JSONArray rows=new JSONArray();
            for(PointerHit hit:lastPointerHits){
                JSONObject row=new JSONObject();row.put("module",hit.module);
                row.put("baseOffset",Long.toUnsignedString(hit.baseOffset,16));
                JSONArray offsets=new JSONArray();for(long offset:hit.offsets)offsets.put(Long.toUnsignedString(offset,16));
                row.put("offsets",offsets);rows.put(row);
            }
            root.put("chains",rows);
            getSharedPreferences("pointer_scan",MODE_PRIVATE).edit().putString("saved_chains",root.toString()).apply();
            toast("Saved "+lastPointerHits.size()+" pointer chains on this device.");
            log("Saved "+lastPointerHits.size()+" pointer chains for PID "+lastPointerPid+".");
        }catch(Exception e){toast("Could not save pointer chains: "+e.getMessage());}
    }
    private void loadPointerChains(){
        String saved=getSharedPreferences("pointer_scan",MODE_PRIVATE).getString("saved_chains",null);
        if(saved==null||saved.isEmpty()){toast("No saved pointer chains found on this device.");return;}
        try{
            JSONObject root=new JSONObject(saved);JSONArray rows=root.optJSONArray("chains");
            if(rows==null)throw new IOException("Saved pointer-chain file is invalid.");
            List<PointerHit> loaded=new ArrayList<>();
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);JSONArray rawOffsets=row.optJSONArray("offsets");
                List<Long> offsets=new ArrayList<>();
                if(rawOffsets!=null)for(int j=0;j<rawOffsets.length();j++)offsets.add(Long.parseUnsignedLong(rawOffsets.getString(j),16));
                loaded.add(new PointerHit(row.optString("module","unknown"),Long.parseUnsignedLong(row.getString("baseOffset"),16),offsets));
            }
            lastPointerHits=loaded;lastPointerPid=root.optInt("pid",-1);
            String targetHex=root.optString("target","0");lastPointerTarget=Long.parseUnsignedLong(targetHex,16);
            StringBuilder out=new StringBuilder("SAVED POINTER CHAINS\nPID: ").append(lastPointerPid)
                .append("\nTarget: 0x").append(Long.toHexString(lastPointerTarget).toUpperCase(Locale.US))
                .append("\nChains: ").append(loaded.size()).append("\n\n");
            for(int i=0;i<loaded.size();i++){
                PointerHit hit=loaded.get(i);out.append(i+1).append(". ").append(hit.module).append("+0x")
                    .append(Long.toHexString(hit.baseOffset).toUpperCase(Locale.US));
                for(long offset:hit.offsets)out.append(" -> 0x").append(Long.toHexString(offset).toUpperCase(Locale.US));
                out.append("\n");
            }
            pointerResult.setText(out.toString());toast("Loaded "+loaded.size()+" saved pointer chains.");
        }catch(Exception e){toast("Could not load saved pointer chains: "+e.getMessage());}
    }

    private void resolvePointerChains(){
        if(!connected){toast("Connect to the PS5 first.");return;}
        if(lastPointerHits.isEmpty()){toast("Run a pointer scan or load saved chains first.");return;}
        pointerCancel=false;
        pointerResult.setText("Resolving saved chains against current process memory…");
        runAsync("Resolving pointer chains",()->{
            int pid=currentPid();List<MapEntry> maps=client.maps(pid);
            Map<String,Long> moduleBases=new HashMap<>();
            for(MapEntry m:maps){
                if(!isPointerModule(m)||m.name==null)continue;
                Long old=moduleBases.get(m.name);
                if(old==null||Long.compareUnsigned(m.start,old)<0)moduleBases.put(m.name,m.start);
            }
            StringBuilder out=new StringBuilder("LIVE POINTER CHAIN RESOLUTION\nPID: ").append(pid)
                .append("\nSaved target: 0x").append(Long.toHexString(lastPointerTarget).toUpperCase(Locale.US))
                .append("\nChains: ").append(lastPointerHits.size()).append("\n\n");
            int resolved=0;
            for(int i=0;i<lastPointerHits.size();i++){
                PointerHit hit=lastPointerHits.get(i);
                out.append(i+1).append(". ").append(hit.module).append("+0x")
                    .append(Long.toHexString(hit.baseOffset).toUpperCase(Locale.US)).append(" -> ");
                try{
                    Long base=moduleBases.get(hit.module);
                    if(base==null)throw new IOException("module not mapped");
                    long address=base+hit.baseOffset;
                    if(hit.offsets.isEmpty())throw new IOException("empty offset chain");
                    for(int level=0;level<hit.offsets.size();level++){
                        if(pointerCancel)throw new IOException("cancelled");
                        byte[] raw=client.read(pid,address,8);
                        if(raw.length!=8)throw new IOException("short pointer read");
                        long pointer=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).getLong();
                        if(pointer==0)throw new IOException("null pointer at level "+(level+1));
                        address=pointer+hit.offsets.get(level);
                    }
                    out.append("0x").append(Long.toHexString(address).toUpperCase(Locale.US));
                    if(address==lastPointerTarget)out.append("  [MATCHES SCAN TARGET]");
                    resolved++;
                }catch(Exception e){out.append("UNRESOLVED: ").append(e.getMessage()==null?"read failed":e.getMessage());}
                out.append("\n");
            }
            out.append("\nResolved: ").append(resolved).append("/").append(lastPointerHits.size());
            return out.toString();
        },value->{pointerResult.setText(value);log("Pointer chain live resolution completed.");});
    }

    private static boolean isPointerModule(MapEntry m){
        String n=m.name==null?"":m.name.toLowerCase(Locale.US);
        return n.equals("executable")||n.matches(".*\\.(bin|elf|self|prx|sprx|so)$");
    }
    private static boolean pointerInRanges(List<MapEntry> ranges,long value){
        int lo=0,hi=ranges.size();
        while(lo<hi){int mid=(lo+hi)>>>1;MapEntry r=ranges.get(mid);
            if(Long.compareUnsigned(value,r.start)<0)hi=mid;
            else if(Long.compareUnsigned(value,r.end)>=0)lo=mid+1;
            else return true;
        }
        return false;
    }
    private static int pointerLowerBound(List<PointerNode> a,long v){int lo=0,hi=a.size();while(lo<hi){int m=(lo+hi)>>>1;if(Long.compareUnsigned(a.get(m).value,v)<0)lo=m+1;else hi=m;}return lo;}
    private static int pointerUpperBound(List<PointerNode> a,long v){int lo=0,hi=a.size();while(lo<hi){int m=(lo+hi)>>>1;if(Long.compareUnsigned(a.get(m).value,v)<=0)lo=m+1;else hi=m;}return lo;}

    private static final class MultiReadEntry {
        final long address; final int length;
        MultiReadEntry(long address,int length){this.address=address;this.length=length;}
    }
    private static final class MultiWriteEntry {
        final long address; final byte[] data;
        MultiWriteEntry(long address,byte[] data){this.address=address;this.data=data;}
    }
    private static final class AddressEntry {
        String name, address, bytes; int type; boolean frozen;
        AddressEntry(String n,String a,String b,int t,boolean f){name=n;address=a;bytes=b;type=t;frozen=f;}
    }
    private void loadAddressEntries() {
        addressEntries.clear();
        try {
            JSONArray arr=new JSONArray(getSharedPreferences("address_table",MODE_PRIVATE).getString("entries","[]"));
            for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);addressEntries.add(new AddressEntry(o.optString("name","Entry"),o.optString("address","0x0"),o.optString("bytes","00"),o.optInt("type",0),o.optBoolean("frozen",false)));}
        } catch(Exception e){log("Saved address table could not be loaded: "+e.getMessage());}
        renderAddressEntries();
        if(hasFrozenAddresses()){addressHandler.removeCallbacks(addressFreezeTick);addressHandler.post(addressFreezeTick);}
    }
    private void importAddressTable() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");
        startActivityForResult(i,REQ_IMPORT_ADDRESS_TABLE);
    }
    private void exportAddressTable() {
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE,"ps5-address-table.json");startActivityForResult(i,REQ_EXPORT_ADDRESS_TABLE);
    }
    private void saveAddressEntries() {
        JSONArray arr=new JSONArray();
        for(AddressEntry e:addressEntries){JSONObject o=new JSONObject();try{o.put("name",e.name);o.put("address",e.address);o.put("bytes",e.bytes);o.put("type",e.type);o.put("frozen",e.frozen);}catch(JSONException ignored){}arr.put(o);}
        getSharedPreferences("address_table",MODE_PRIVATE).edit().putString("entries",arr.toString()).apply();
    }
    private void addAddressEntry() {
        try {
            String n=addressEntryName.getText().toString().trim(),a=addressEntryAddress.getText().toString().trim(),raw=addressEntryBytes.getText().toString().trim(); int t=addressEntryType.getSelectedItemPosition(); String b=toHex(encodeAddressValue(t,raw));
            if(a.isEmpty()||b.isEmpty())throw new IllegalArgumentException("Enter an address and a value.");
            addressEntries.add(new AddressEntry(n.isEmpty()?"Entry "+(addressEntries.size()+1):n,a,b,t,false));
            saveAddressEntries();renderAddressEntries();log("Address added: "+a);
        } catch(Exception e){toast(e.getMessage());}
    }
    private void renderAddressEntries() {
        if(addressRows==null)return;
        addressRows.removeAllViews();
        for(int i=0;i<addressEntries.size();i++){
            final int index=i;AddressEntry e=addressEntries.get(i);
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(8),dp(6),dp(8),dp(6));card.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL));
            TextView label=text(e.name+"  "+e.address+"\n"+addressTypeName(e.type)+": "+decodeAddressValue(e.type,parseHex(e.bytes))+"  ["+e.bytes+"]"+(e.frozen?"  [FROZEN]":""),12,TEXT,true);label.setTypeface(Typeface.MONOSPACE);card.addView(label);
            LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.VERTICAL);
            LinearLayout firstRow=new LinearLayout(this);firstRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout secondRow=new LinearLayout(this);secondRow.setOrientation(LinearLayout.HORIZONTAL);
            Button thirdRowCopy=button("COPY AS CHEAT",PANEL);
            Button read=button("READ",PANEL),edit=button("EDIT",PANEL),write=button("WRITE",ACCENT);
            Button freeze=button(e.frozen?"UNFREEZE":"FREEZE",e.frozen?GREEN:PANEL),remove=button("REMOVE",PANEL);
            for(Button b:new Button[]{read,edit,write})firstRow.addView(b,new LinearLayout.LayoutParams(0,dp(44),1));
            for(Button b:new Button[]{freeze,remove})secondRow.addView(b,new LinearLayout.LayoutParams(0,dp(44),1));
            actions.addView(firstRow);actions.addView(secondRow);actions.addView(thirdRowCopy);card.addView(actions);
            thirdRowCopy.setOnClickListener(v->copyAddressAsCheat(index));
            addressRows.addView(card,new LinearLayout.LayoutParams(-1,-2));
            read.setOnClickListener(v->readAddressEntry(index));
            edit.setOnClickListener(v->editAddressEntry(index));
            write.setOnClickListener(v->writeAddressEntry(index));
            freeze.setOnClickListener(v->{addressEntries.get(index).frozen=!addressEntries.get(index).frozen;saveAddressEntries();renderAddressEntries();if(hasFrozenAddresses()){addressHandler.removeCallbacks(addressFreezeTick);addressHandler.post(addressFreezeTick);}else addressHandler.removeCallbacks(addressFreezeTick);});
            remove.setOnClickListener(v->{addressEntries.remove(index);saveAddressEntries();renderAddressEntries();});
        }
    }
    private void copyAddressAsCheat(int index){
        if(gameFile==null){toast("Create or import a game file first.");return;}
        if(index<0||index>=addressEntries.size())return;
        AddressEntry entry=addressEntries.get(index);
        try{
            JSONArray cheats=gameFile.optJSONArray("cheats");
            if(cheats==null){cheats=new JSONArray();gameFile.put("cheats",cheats);}
            JSONObject step=new JSONObject().put("op","patch").put("addr",entry.address).put("type","hex").put("value",entry.bytes);
            JSONArray steps=new JSONArray().put(step);
            JSONObject cheat=new JSONObject().put("name",entry.name).put("mode","toggle").put("enabled",true).put("steps",steps);
            cheats.put(cheat);persistGameFile();refreshCheatList();
            log("Copied address '"+entry.name+"' into the game cheat library as a toggle patch.");
            toast("Address copied as a cheat.");
        }catch(Exception e){toast("Could not create cheat: "+e.getMessage());}
    }
    private void readAddressEntry(int index) {
        if(!connected){toast("Connect to the PS5 first.");return;}
        AddressEntry e=addressEntries.get(index);
        runAsync("Reading address entry",()->{long a=resolveAddress(e.address,currentPid());byte[] data=client.read(currentPid(),a,parseHex(e.bytes).length);e.bytes=toHex(data);saveAddressEntries();return e.name+": "+e.bytes;},r->{renderAddressEntries();log(r);});
    }
    private void writeAddressEntry(int index) {
        if(!connected || client==null){toast("Connect to the PS5 first.");return;}
        if(index<0 || index>=addressEntries.size()){toast("Address entry no longer exists.");return;}
        AddressEntry entry=addressEntries.get(index);
        runAsync("Writing address entry",()->{
            int p=currentPid();
            long address=resolveAddress(entry.address,p);
            byte[] value=parseHex(entry.bytes);
            if(value.length==0 || value.length>4096)throw new IllegalArgumentException("Address value must contain 1–4096 bytes.");
            client.write(p,address,value);
            byte[] verify=client.read(p,address,value.length);
            if(!Arrays.equals(value,verify))throw new IOException("Write sent, but read-back verification did not match.");
            entry.bytes=toHex(verify);
            saveAddressEntries();
            return "Verified write: "+entry.name+" at 0x"+Long.toHexString(address).toUpperCase(Locale.US);
        },result->{renderAddressEntries();log(result);toast("Memory value written and verified.");});
    }
    private void editAddressEntry(int index) {
        AddressEntry e=addressEntries.get(index);LinearLayout box=new LinearLayout(this);box.setPadding(dp(12),0,dp(12),0);box.setOrientation(LinearLayout.VERTICAL);
        EditText name=field("Name",e.name),addr=field("Address",e.address),bytes=field("Value",decodeAddressValue(e.type,parseHex(e.bytes))); Spinner type=new Spinner(this);String[] types={"Hex bytes","Byte (unsigned)","Byte (signed)","2 bytes (unsigned)","2 bytes (signed)","4 bytes (unsigned)","4 bytes (signed)","8 bytes (unsigned)","8 bytes (signed)","Float","Double"};type.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,types));type.setSelection(e.type);box.addView(name);box.addView(addr);box.addView(type);box.addView(bytes);
        new android.app.AlertDialog.Builder(this).setTitle("Edit address entry").setView(box).setNegativeButton("Cancel",null).setPositiveButton("SAVE",(d,w)->{try{int t=type.getSelectedItemPosition();byte[] encoded=encodeAddressValue(t,bytes.getText().toString().trim());e.name=name.getText().toString().trim();e.address=addr.getText().toString().trim();e.type=t;e.bytes=toHex(encoded);saveAddressEntries();renderAddressEntries();}catch(Exception ex){toast(ex.getMessage());}}).show();
    }
    private String addressTypeName(int t){String[] n={"HEX","U8","S8","U16","S16","U32","S32","U64","S64","FLOAT","DOUBLE"};return t>=0&&t<n.length?n[t]:"HEX";}
    private byte[] encodeAddressValue(int t,String raw){
        if(t==0)return parseHex(raw);
        ByteBuffer b;
        switch(t){
            case 1:case 2:return new byte[]{(byte)Long.decode(raw).longValue()};
            case 3:case 4:b=ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);b.putShort((short)Long.decode(raw).longValue());return b.array();
            case 5:case 6:b=ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);b.putInt((int)Long.decode(raw).longValue());return b.array();
            case 7:case 8:b=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);b.putLong(Long.decode(raw));return b.array();
            case 9:b=ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);b.putFloat(Float.parseFloat(raw));return b.array();
            case 10:b=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);b.putDouble(Double.parseDouble(raw));return b.array();
            default:throw new IllegalArgumentException("Unsupported address value type.");
        }
    }
    private String decodeAddressValue(int t,byte[] raw){
        if(t==0)return toHex(raw);
        if(raw.length!=(t==1||t==2?1:t==3||t==4?2:t==5||t==6||t==9?4:8))return "Invalid byte length";
        ByteBuffer b=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        switch(t){case 1:return Long.toString(raw[0]&255);case 2:return Byte.toString(raw[0]);case 3:return Integer.toString(b.getShort()&65535);case 4:return Short.toString(b.getShort());case 5:return Long.toString(b.getInt()&0xffffffffL);case 6:return Integer.toString(b.getInt());case 7:return Long.toUnsignedString(b.getLong());case 8:return Long.toString(b.getLong());case 9:return Float.toString(b.getFloat());case 10:return Double.toString(b.getDouble());default:return toHex(raw);}
    }
    private void refreshAddressSnapshot(List<AddressEntry> entries,int processId) throws Exception {
        // Keep each response bounded and avoid thousands of single-command round trips.
        final int maxBatch=256;
        for(int start=0;start<entries.size();start+=maxBatch){
            int end=Math.min(entries.size(),start+maxBatch);
            List<MultiReadEntry> requests=new ArrayList<>();
            for(int i=start;i<end;i++){
                AddressEntry e=entries.get(i);int length=parseHex(e.bytes).length;
                if(length<1||length>4096)throw new IllegalArgumentException("Invalid read size for "+e.name);
                requests.add(new MultiReadEntry(resolveAddress(e.address,processId),length));
            }
            List<byte[]> values=client.readMulti(processId,requests);
            for(int i=0;i<values.size();i++)entries.get(start+i).bytes=toHex(values.get(i));
        }
    }
    private void refreshAddressEntries() {
        if(!connected){toast("Connect to the PS5 first.");return;}
        runAsync("Refreshing address table with bulk reads",()->{
            List<AddressEntry> snapshot=new ArrayList<>(addressEntries);
            refreshAddressSnapshot(snapshot,currentPid());
            for(int i=0;i<snapshot.size();i++)addressEntries.get(i).bytes=snapshot.get(i).bytes;
            saveAddressEntries();return "Bulk-refreshed "+snapshot.size()+" address entries.";
        },r->{renderAddressEntries();log(r);});
    }
    private void refreshAddressEntriesQuietly(){
        if(!connected||addressRefreshBusy||addressEntries.isEmpty())return;
        addressRefreshBusy=true;
        worker.execute(()->{try{
            List<AddressEntry> snapshot=new ArrayList<>(addressEntries);
            refreshAddressSnapshot(snapshot,currentPid());
            for(int i=0;i<snapshot.size();i++)addressEntries.get(i).bytes=snapshot.get(i).bytes;
            saveAddressEntries();
            main.post(()->{addressRefreshBusy=false;renderAddressEntries();});
        }catch(Exception ex){addressRefreshBusy=false;log("Auto-refresh stopped: "+ex.getMessage());addressAutoRefresh=false;main.post(()->{if(addressAutoButton!=null)addressAutoButton.setText("AUTO REFRESH: OFF");});}});
    }
    private boolean hasFrozenAddresses(){for(AddressEntry e:addressEntries)if(e.frozen)return true;return false;}
    private void setAllAddressFrozen(boolean freeze) {
        if(addressEntries.isEmpty()){toast("Address list is empty.");return;}
        for(AddressEntry entry:addressEntries)entry.frozen=freeze;
        saveAddressEntries();
        renderAddressEntries();
        addressHandler.removeCallbacks(addressFreezeTick);
        if(freeze){
            if(connected)addressHandler.post(addressFreezeTick);
            else toast("All entries marked frozen. Connect to the PS5 to apply writes.");
        } else {
            toast("All address entries unfrozen.");
        }
        log((freeze?"Bulk freeze enabled for ":"Bulk freeze disabled for ")+addressEntries.size()+" address entries.");
    }
    private void refreshFrozenAddresses() {
        if(!connected || client==null || freezeWriteBusy)return;
        List<AddressEntry> frozen=new ArrayList<>();
        for(AddressEntry e:addressEntries)if(e.frozen)frozen.add(new AddressEntry(e.name,e.address,e.bytes,e.type,e.frozen));
        if(frozen.isEmpty())return;
        freezeWriteBusy=true;
        worker.execute(()->{
            try {
                int p=currentPid();
                List<MultiWriteEntry> batch=new ArrayList<>();
                for(AddressEntry e:frozen){
                    if(!connected || client==null)break;
                    byte[] value=parseHex(e.bytes);
                    if(value.length==0 || value.length>4096)throw new IllegalArgumentException("Invalid frozen value for "+e.name);
                    long a=resolveAddress(e.address,p);
                    batch.add(new MultiWriteEntry(a,value));
                }
                if(batch.isEmpty())return;
                byte[] statuses=client.writeMulti(p,batch);
                int applied=0;
                for(int i=0;i<statuses.length;i++){
                    int status=statuses[i]&255;
                    if(status==0)applied++;
                    else log("Freeze entry '"+frozen.get(i).name+"' failed (payload status "+status+").");
                }
                log("Bulk freeze cycle: "+applied+"/"+batch.size()+" entries applied and payload-verified.");
            } catch(Exception ex) {
                log("Bulk freeze write failed: "+ex.getMessage());
            } finally {
                freezeWriteBusy=false;
            }
        });
    }

    private void scanStringPattern(){
        try{
            String value=scanStringInput.getText().toString();
            if(value.isEmpty())throw new IllegalArgumentException("Enter a non-empty text string.");
            byte[] bytes=value.getBytes(scanStringEncoding.getSelectedItemPosition()==0?StandardCharsets.UTF_8:StandardCharsets.UTF_16LE);
            if(bytes.length>64)throw new IllegalArgumentException("Encoded string exceeds the 64-byte exact scanner pattern limit.");
            scanBytes.setText(toHex(bytes));
            scanRange();
        }catch(Exception e){toast("String scan: "+e.getMessage());}
    }

    private void scanRange() {
        if (!connected) { toast("Connect to the PS5 first."); return; }
        if (exactScanRunning) { toast("An exact byte scan is already running."); return; }
        exactScanRunning = true;
        exactScanCancel = false;
        lastExactMatches.clear();lastExactPattern=new byte[0];
        if(importExactMatchesButton!=null)importExactMatchesButton.setEnabled(false);
        runAsync("Scanning memory range", () -> {
            try {
            int p = currentPid();
            long start = resolveAddress(scanAddress.getText().toString().trim(), p);
            int length = Integer.decode(scanLength.getText().toString().trim());
            int maxMatches=Integer.parseInt(scanMaxMatches.getText().toString().trim());
            if(maxMatches<1||maxMatches>2000)throw new IllegalArgumentException("Maximum matches must be 1–2000.");
            String patternText=scanBytes.getText().toString().trim();
            String[] tokens=patternText.split("[\\s,]+");
            if(tokens.length==0||tokens.length>64)throw new IllegalArgumentException("Pattern must contain 1–64 byte tokens.");
            byte[] pattern=new byte[tokens.length];boolean[] mask=new boolean[tokens.length];
            for(int k=0;k<tokens.length;k++){String token=tokens[k].trim();if(token.equals("?")||token.equals("??")){mask[k]=false;pattern[k]=0;}else{if(!token.matches("(?i)[0-9a-f]{2}"))throw new IllegalArgumentException("Use two-digit hex bytes or ?? wildcards.");pattern[k]=(byte)Integer.parseInt(token,16);mask[k]=true;}}
            int anchor=-1;boolean exactPattern=true;
            for(int k=0;k<mask.length;k++){if(mask[k])anchor=k;else exactPattern=false;}
            if(anchor<0)throw new IllegalArgumentException("Pattern cannot contain only wildcards; include at least one fixed byte.");
            int[] badCharacterSkip=null;
            if(exactPattern){
                badCharacterSkip=new int[256];Arrays.fill(badCharacterSkip,pattern.length);
                for(int k=0;k<pattern.length-1;k++)badCharacterSkip[pattern[k]&255]=pattern.length-1-k;
            }
            if (length <= 0 || length > SCAN_MAX) throw new IllegalArgumentException("Scan range must be between 1 byte and 16 MiB.");
            List<Long> matches = new ArrayList<>();
            int chunkSize = 64 * 1024;
            byte[] tail = new byte[0];
            long scanned = 0;
            while (scanned < length && matches.size() < maxMatches) {
                if (exactScanCancel) break;
                int n = (int)Math.min(chunkSize, length - scanned);
                byte[] block = client.read(p, start + scanned, n);
                byte[] combined = new byte[tail.length + block.length];
                System.arraycopy(tail, 0, combined, 0, tail.length);
                System.arraycopy(block, 0, combined, tail.length, block.length);
                long combinedBase = start + scanned - tail.length;
                int i=0,lastCandidate=combined.length-pattern.length;
                while(i<=lastCandidate){
                    boolean same=true;
                    if(exactPattern){
                        int j=pattern.length-1;
                        while(j>=0&&combined[i+j]==pattern[j])j--;
                        same=j<0;
                    }else{
                        // Check the rightmost fixed anchor before full wildcard comparison.
                        if(combined[i+anchor]!=pattern[anchor])same=false;
                        else for(int j=0;j<pattern.length;j++)if(mask[j]&&combined[i+j]!=pattern[j]){same=false;break;}
                    }
                    if(same){
                        long at=combinedBase+i;
                        if(at>=start+scanned-tail.length)matches.add(at);
                        if(matches.size()>=maxMatches)break;
                    }
                    if(exactPattern){
                        int shift=badCharacterSkip[combined[i+pattern.length-1]&255];
                        i+=Math.max(1,shift);
                    }else i++;
                }
                int keep = Math.min(pattern.length - 1, combined.length);
                tail = Arrays.copyOfRange(combined, combined.length - keep, combined.length);
                scanned += n;
            }
            StringBuilder out = new StringBuilder();
            out.append(exactScanCancel ? "Scan cancelled after " : "Scanned ").append(scanned).append(" bytes from 0x").append(Long.toHexString(start).toUpperCase(Locale.US));
            out.append("\nPattern: ").append(toHex(pattern)).append("\nMatches: ").append(matches.size());
            for (long m : matches) out.append("\n0x").append(Long.toHexString(m).toUpperCase(Locale.US));
            if (matches.size() == maxMatches && scanned < length) out.append("\n(match limit reached; increase the limit to collect more)");
            lastExactMatches=new ArrayList<>(matches);lastExactPattern=pattern.clone();
            return out.toString();
            } finally { exactScanRunning = false; exactScanCancel = false; }
        }, value -> {scanResult.setText(value);importExactMatchesButton.setEnabled(!lastExactMatches.isEmpty());});
    }

    private void importExactMatchesToAddressTable(){
        if(!connected){toast("Connect to the PS5 to read the actual matched bytes before importing.");return;}
        if(lastExactMatches.isEmpty()||lastExactPattern.length==0){toast("Run an exact scan first.");return;}
        final List<Long> matches=new ArrayList<>(lastExactMatches);final int byteLength=lastExactPattern.length;
        runAsync("Reading actual bytes for scan matches",()->{
            int p=currentPid();List<AddressEntry> loaded=new ArrayList<>();
            for(int offset=0;offset<matches.size();offset+=256){
                int end=Math.min(matches.size(),offset+256);List<MultiReadEntry> requests=new ArrayList<>();
                for(int n=offset;n<end;n++)requests.add(new MultiReadEntry(matches.get(n),byteLength));
                List<byte[]> values=client.readMulti(p,requests);
                if(values.size()!=requests.size())throw new IOException("Bulk read returned an incomplete match page.");
                for(int n=0;n<values.size();n++)loaded.add(new AddressEntry("AOB "+String.format(Locale.US,"%016X",matches.get(offset+n)),"0x"+Long.toHexString(matches.get(offset+n)),toHex(values.get(n)),0,false));
            }
            return loaded;
        },loaded->{
            int added=0,skipped=0;
            for(AddressEntry candidate:loaded){
                boolean exists=false;for(AddressEntry e:addressEntries)if(e.address.equalsIgnoreCase(candidate.address)){exists=true;break;}
                if(exists){skipped++;continue;}addressEntries.add(candidate);added++;
            }
            saveAddressEntries();renderAddressEntries();
            log("Imported "+added+" scan matches using live memory bytes; "+skipped+" duplicates skipped.");
            toast("Added "+added+" matches; "+skipped+" duplicates skipped.");
        });
    }

    private byte[] encodeTurboValue(int type, String raw) {
        ByteBuffer b;
        switch(type) {
            case 0: case 1: return new byte[]{(byte)Long.decode(raw).longValue()};
            case 2: case 3: b=ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN); b.putShort((short)Long.decode(raw).longValue()); return b.array();
            case 4: case 5: b=ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN); b.putInt((int)Long.decode(raw).longValue()); return b.array();
            case 6: case 7: b=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN); b.putLong(Long.decode(raw)); return b.array();
            case 8: b=ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN); b.putInt(Float.floatToIntBits(Float.parseFloat(raw))); return b.array();
            case 9: b=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN); b.putLong(Double.doubleToLongBits(Double.parseDouble(raw))); return b.array();
            default: throw new IllegalArgumentException("Unsupported value type.");
        }
    }
    private void ensureTurboSession() throws Exception {
        if(scanClient==null || !scanClient.isConnected()) {
            scanClient=new PS5Client();
            scanClient.connect(host.getText().toString().trim(),Integer.parseInt(port.getText().toString().trim()));
        }
        if(scanClient.turboCaps()==null) throw new IOException("Payload does not advertise TurboScan. Update ps5debug-NG or use Exact Byte Scan.");
        scanClient.auth();
    }
    private byte[] encodeTurboCompareValue(int type,int compare,String raw) {
        if(compare==5||compare==7||compare==9||compare==10||compare==11) return new byte[0];
        if(compare==4) { String[] pair=raw.split(",",2); if(pair.length!=2) throw new IllegalArgumentException("Between scan needs two comma-separated values: low,high"); byte[] lo=encodeTurboValue(type,pair[0].trim()),hi=encodeTurboValue(type,pair[1].trim()); byte[] both=Arrays.copyOf(lo,lo.length+hi.length);System.arraycopy(hi,0,both,lo.length,hi.length);return both; }
        return encodeTurboValue(type,raw);
    }
    private static final int[] TURBO_COMPARE_CODES={0,1,2,3,4,5,6,7,8,9,10,11};
    private void turboFirstScan() {
        if(!connected) { toast("Connect to the PS5 first."); return; }
        runAsync("Starting TurboScan",()->{
            ensureTurboSession(); int p=currentPid();
            long start=resolveAddress(scanAddress.getText().toString().trim(),p);
            int length=Integer.decode(scanLength.getText().toString().trim());
            if(length<1) throw new IllegalArgumentException("Enter a positive scan range.");
            int type=turboType.getSelectedItemPosition(); int ci=turboCompare.getSelectedItemPosition();
            if(ci>4 && ci!=11) throw new IllegalArgumentException("First scan supports Exact, Fuzzy, Bigger, Smaller, Between or Unknown Initial Value.");
            if(ci==1 && type!=8 && type!=9) throw new IllegalArgumentException("Fuzzy (±1) scan is supported only for Float and Double.");
            int compare=TURBO_COMPARE_CODES[ci]; byte[] value=encodeTurboCompareValue(type,compare,turboValue.getText().toString().trim());
            List<TurboSegment> segments=buildTurboSegments(p); if(segments.isEmpty()) throw new IllegalArgumentException("No readable memory regions match the selected filters.");
            long count=compare==11?scanClient.turboUnknownFirstScan(p,segments,type,parseTurboAlignment()):scanClient.turboFirstScan(p,segments,type,compare,value,parseTurboAlignment());
            turboActive=true; turboValueType=type; turboValueLength=(type==4||type==5?4:type==6||type==7||type==9?8:type==8?4:type==2||type==3?2:1); turboResultTotal=count;
            long totalBytes=0; for(TurboSegment seg:segments) totalBytes+=seg.length;
            return "TurboScan started. Results: "+count+"\\nRegions: "+segments.size()+" | Selected memory: "+(totalBytes/1048576)+" MiB";
        },result->{turboResultStart.setText("0");lastTurboResults.clear();renderTurboRows();turboResult.setText(result);});
    }
    private int parseTurboAlignment() {
        int a=Integer.parseInt(turboAlignment.getText().toString().trim());
        if(a<1||a>255) throw new IllegalArgumentException("Alignment must be between 1 and 255.");
        return turboFastAlignment.isChecked()?a:1;
    }
    private List<TurboSegment> buildTurboSegments(int p) throws Exception {
        long min=turboStartAddress.getText().toString().trim().isEmpty()?0L:resolveAddress(turboStartAddress.getText().toString().trim(),p);
        long max=turboStopAddress.getText().toString().trim().isEmpty()?Long.MAX_VALUE:resolveAddress(turboStopAddress.getText().toString().trim(),p);
        if(max<=min) throw new IllegalArgumentException("Stop address must be greater than start address.");
        if((scanClient.turboEngines&0x10)==0) throw new IllegalStateException("This PS5 payload does not advertise disjoint-region TurboScan support. Update the PS5Debug payload before using filtered scans.");
        List<TurboSegment> out=new ArrayList<>();
        if(turboSkipSlow.isChecked()) {
            List<PS5Client.TurboRegion> regions=scanClient.turboRegions(p);
            for(PS5Client.TurboRegion r:regions) {
                if((r.prot&1)==0) continue;
                if(turboWritableOnly.isChecked()&&(r.prot&2)==0) continue;
                if(!turboIncludeExec.isChecked()&&(r.prot&4)!=0) continue;
                if((r.flags&1)!=0||r.mbps<100) continue;
                long a=Math.max(r.start,min), b=Math.min(r.end,max);
                while(b>a) { int n=(int)Math.min(b-a,0x40000000L); if(n>0)out.add(new TurboSegment(a,n)); a+=n; }
            }
        } else {
            List<MapEntry> maps=scanClient.maps(p);
            for(MapEntry m:maps) {
                if((m.prot&1)==0) continue;
                if(turboWritableOnly.isChecked()&&(m.prot&2)==0) continue;
                if(!turboIncludeExec.isChecked()&&(m.prot&4)!=0) continue;
                long a=Math.max(m.start,min), b=Math.min(m.end,max);
                while(b>a) { int n=(int)Math.min(b-a,0x40000000L); if(n>0)out.add(new TurboSegment(a,n)); a+=n; }
            }
        }
        out.sort((x,y)->Long.compare(x.start,y.start));
        List<TurboSegment> merged=new ArrayList<>();
        for(TurboSegment x:out) {
            if(!merged.isEmpty()) { TurboSegment last=merged.get(merged.size()-1);
                if(last.start+(long)last.length==x.start && (long)last.length+x.length<=0x40000000L) { merged.set(merged.size()-1,new TurboSegment(last.start,last.length+x.length)); continue; } }
            merged.add(x);
        }
        return merged;
    }
    private void turboNextScan() {
        if(!connected || !turboActive || scanClient==null) { toast("Run Turbo First Scan first."); return; }
        runAsync("Refining TurboScan",()->{
            if(turboType.getSelectedItemPosition()!=turboValueType) throw new IllegalArgumentException("Value type cannot change during a scan.");
            int ci=turboCompare.getSelectedItemPosition(); if(ci==11) throw new IllegalArgumentException("Unknown Initial Value is only available for First Scan.");
             if(ci==1 && turboValueType!=8 && turboValueType!=9) throw new IllegalArgumentException("Fuzzy (±1) scan is supported only for Float and Double.");
            int compare=TURBO_COMPARE_CODES[ci];
            byte[] value=encodeTurboCompareValue(turboValueType,compare,turboValue.getText().toString().trim());
            turboResultTotal=scanClient.turboNextScan(compare,value,turboValueLength);
            return "Next Scan complete. Remaining results: "+turboResultTotal;
        },result->{turboResultStart.setText("0");lastTurboResults.clear();renderTurboRows();turboResult.setText(result);});
    }
    private void moveTurboPage(int direction) {
        try {
            int start=Integer.parseInt(turboResultStart.getText().toString().trim());
            int count=Integer.parseInt(turboResultCount.getText().toString().trim());
            if(count<1||count>500) throw new IllegalArgumentException("Page size must be 1–500.");
            long next=(long)start+(long)direction*count;
            if(next<0) { toast("Already at the first results page."); return; }
            if(next>=turboResultTotal) { toast("Already at the last results page."); return; }
            turboResultStart.setText(String.valueOf(next));
            turboLoadResults();
        } catch(Exception e) { toast(e.getMessage()==null?"Invalid page settings.":e.getMessage()); }
    }
    private void turboLoadResults() {
        if(!turboActive || scanClient==null) { toast("Run Turbo First Scan first."); return; }
        runAsync("Loading TurboScan results",()->{
            int start=Integer.parseInt(turboResultStart.getText().toString().trim());
            int count=Integer.parseInt(turboResultCount.getText().toString().trim());
            if(start<0||count<1||count>500) throw new IllegalArgumentException("Result page must start at 0 or higher and contain 1–500 rows.");
            if(start>=turboResultTotal) throw new IllegalArgumentException("Page start is beyond the current result count.");
            List<PS5Client.TurboResult> rows=scanClient.turboGet(start,count,turboValueLength); lastTurboResults=rows;
            StringBuilder b=new StringBuilder("Results ").append(start).append("–").append(start+rows.size()-1).append(" of ").append(turboResultTotal).append(":\\n");
            for(PS5Client.TurboResult r:rows) b.append(String.format(Locale.US,"0x%016X  current=%s  previous=%s\\n",r.address,toHex(r.current),toHex(r.previous)));
            return b.toString();
        },result->{turboResult.setText(result);renderTurboRows();});
    }
    private void renderTurboRows() {
        turboRows.removeAllViews();
        for(PS5Client.TurboResult item:lastTurboResults) {
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(dp(9),dp(7),dp(9),dp(7));row.setBackgroundColor(PANEL);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(4);turboRows.addView(row,lp);
            TextView label=text(String.format(Locale.US,"0x%016X\\nCurrent: %s\\nPrevious: %s",item.address,toHex(item.current),toHex(item.previous)),11,TEXT,false);label.setTypeface(Typeface.MONOSPACE);row.addView(label);
            LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
            Button refresh=button("READ LIVE",PANEL), edit=button("EDIT VALUE (HEX)",PANEL), save=button("SAVE TO TABLE",PANEL);
            actions.addView(refresh,new LinearLayout.LayoutParams(0,dp(48),1));
            actions.addView(edit,new LinearLayout.LayoutParams(0,dp(48),1));
            actions.addView(save,new LinearLayout.LayoutParams(0,dp(48),1));
            row.addView(actions);
            refresh.setOnClickListener(v->refreshTurboResult(item,label,refresh));
            edit.setOnClickListener(v->editTurboResult(item,label));
            save.setOnClickListener(v->saveTurboResultToAddressTable(item));
        }
    }
    private void refreshTurboResult(PS5Client.TurboResult item,TextView label,Button button) {
        if(!connected||client==null){toast("Connect to the PS5 first.");return;}
        runAsync("Reading live scan value",()->toHex(client.read(currentPid(),item.address,item.current.length)),hex->{
            try {
                item.current=parseHex(hex);
                label.setText(String.format(Locale.US,"0x%016X\\nCurrent (live): %s\\nPrevious (scan): %s",item.address,toHex(item.current),toHex(item.previous)));
                log("Live value refreshed at 0x"+Long.toHexString(item.address).toUpperCase(Locale.US));
            } catch(Exception e) { toast("Could not display refreshed value: "+e.getMessage()); }
        });
    }
    private void saveTurboPageToAddressTable(){
        if(lastTurboResults.isEmpty()){toast("Load a TurboScan result page first.");return;}
        int added=0,skipped=0;
        for(PS5Client.TurboResult item:lastTurboResults){
            String address=String.format(Locale.US,"0x%016X",item.address);boolean exists=false;
            for(AddressEntry entry:addressEntries)if(entry.address.equalsIgnoreCase(address)){exists=true;break;}
            if(exists){skipped++;continue;}
            addressEntries.add(new AddressEntry("Turbo result "+(addressEntries.size()+1),address,toHex(item.current),0,false));added++;
        }
        saveAddressEntries();renderAddressEntries();
        log("Saved TurboScan page: "+added+" added, "+skipped+" already present.");
        toast("Saved "+added+" result(s); skipped "+skipped+" duplicates.");
    }
    private void saveTurboResultToAddressTable(PS5Client.TurboResult item) {
        String address=String.format(Locale.US,"0x%016X",item.address);
        for(AddressEntry entry:addressEntries) {
            if(entry.address.equalsIgnoreCase(address)) { toast("This address is already in the address table."); return; }
        }
        addressEntries.add(new AddressEntry("Turbo result "+(addressEntries.size()+1),address,toHex(item.current),0,false));
        saveAddressEntries();
        renderAddressEntries();
        log("Saved scan result to address table: "+address);
        toast("Saved to address table. You can now read, edit, freeze or remove it.");
    }
    private void editTurboResult(PS5Client.TurboResult item, TextView label) {
        if(!connected){toast("Connect to the PS5 first.");return;}
        final long address=item.address;
        final byte[] current=item.current.clone();
        EditText value=new EditText(this);value.setSingleLine(true);value.setText(toHex(current));value.setTextColor(TEXT);value.setHintTextColor(MUTED);
        value.setTypeface(Typeface.MONOSPACE);
        new android.app.AlertDialog.Builder(this).setTitle("Edit memory at 0x"+Long.toHexString(address).toUpperCase(Locale.US)).setMessage("Enter exactly "+current.length+" byte(s) in hexadecimal. The write will be read back and verified.").setView(value).setNegativeButton("Cancel",null).setPositiveButton("WRITE",(d,w)->{
            try {
                byte[] patch=parseHex(value.getText().toString());
                if(patch.length!=current.length)throw new IllegalArgumentException("New value must be exactly "+current.length+" byte(s).");
                runAsync("Editing found memory value",()->{
                    int processId=currentPid();
                    client.write(processId,address,patch);
                    byte[] actual=client.read(processId,address,patch.length);
                    if(!Arrays.equals(actual,patch))throw new IOException("Read-back verification failed. Expected "+toHex(patch)+" but read "+toHex(actual)+".");
                    return "Updated and verified memory at 0x"+Long.toHexString(address).toUpperCase(Locale.US);
                },result->{
                    item.current=patch.clone();
                    label.setText(String.format(Locale.US,"0x%016X\\nCurrent: %s\\nPrevious: %s",item.address,toHex(item.current),toHex(item.previous)));
                    log(result);
                });
            } catch(Exception e){toast(e.getMessage());}
        }).show();
    }
    private void turboCancelScan() {
        if(!connected || !turboActive || client==null) { toast("No running TurboScan to cancel."); return; }
        final int cancelPid;
        try { cancelPid=currentPid(); } catch(Exception e) { toast(e.getMessage()); return; }
        log("Sending TurboScan cancellation request…");
        cancelWorker.execute(()->{
            try { client.turboCancel(cancelPid); main.post(()->{turboActive=false;turboResult.setText("TurboScan cancellation requested. Start a new scan when ready.");}); }
            catch(Exception e){String m=e.getMessage()==null?e.toString():e.getMessage();main.post(()->toast("Cancel failed: "+m));}
        });
    }

    private void turboEndScan() {
        if(scanClient==null || !turboActive) { toast("No TurboScan session."); return; }
        runAsync("Ending TurboScan",()->{scanClient.turboEnd();turboActive=false;return "TurboScan session ended.";},result->turboResult.setText(result));
    }

    private void loadMaps() {
        if (!connected) { toast("Connect to the PS5 first."); return; }
        runAsync("Loading memory maps", () -> {
            List<MapEntry> list = client.maps(currentPid());
            String filter = mapFilter.getText().toString().trim().toLowerCase(Locale.US);
            StringBuilder b = new StringBuilder("Regions: ").append(list.size()).append("\\n");
            int shown = 0;
            for (MapEntry e : list) {
                if (!filter.isEmpty() && !e.name.toLowerCase(Locale.US).contains(filter)) continue;
                b.append(String.format(Locale.US, "%016X-%016X  %s  prot=%d  off=%X\\n", e.start, e.end, e.name, e.prot, e.offset));
                if (++shown >= 500) { b.append("… output capped at 500 rows"); break; }
            }
            return b.toString();
        }, mapResult::setText);
    }

    private void disassembleRegion() {
        if (!connected) { toast("Connect to the PS5 first."); return; }
        runAsync("Disassembling region", () -> {
            int p = currentPid();
            long addr = resolveAddress(disasmAddress.getText().toString().trim(), p);
            int len = Integer.decode(disasmLength.getText().toString().trim());
            if (len < 1 || len > 4096) throw new IllegalArgumentException("Decode range must be 1–4096 bytes.");
            List<DisasmEntry> entries = client.disassemble(p, addr, len, 256);
            StringBuilder b = new StringBuilder("Address       Size  Kind  Bytes\\n");
            for (DisasmEntry e : entries) b.append(String.format(Locale.US, "%016X  %2d    %02X    %s\\n", e.address, e.length, e.kind, toHex(e.bytes)));
            if (entries.isEmpty()) b.append("No instructions returned.");
            return b.toString();
        }, disasmResult::setText);
    }

    private void findXrefs() {
        if (!connected) { toast("Connect to the PS5 first."); return; }
        runAsync("Finding references", () -> {
            int p = currentPid();
            long target = resolveAddress(xrefTarget.getText().toString().trim(), p);
            List<MapEntry> regions = client.maps(p);
            StringBuilder b = new StringBuilder("RIP-relative references to ").append(String.format("0x%X", target)).append(":\\n");
            int total = 0;
            for (MapEntry m : regions) {
                if ((m.prot & 4) == 0) continue;
                long size = Math.min(m.end - m.start, 1024 * 1024);
                if (size <= 0) continue;
                List<Long> hits = client.findXrefs(p, m.start, (int)size, target);
                for (long h : hits) { b.append(String.format("0x%016X\\n", h)); if (++total >= 300) break; }
                if (total >= 300) break;
            }
            if (total == 0) b.append("No references found in readable executable regions (first 1 MiB per region).");
            return b.toString();
        }, disasmResult::setText);
    }

    private void scanCodeCaves() {
        if(!connected){toast("Connect to the PS5 first.");return;}
        final int minSize,budgetMiB;
        try{
            minSize=Integer.parseInt(caveMinSize.getText().toString().trim());
            budgetMiB=Integer.parseInt(caveMaxMiB.getText().toString().trim());
            if(minSize<4||minSize>65536)throw new IllegalArgumentException("Minimum cave size must be 4–65536 bytes.");
            if(budgetMiB<1||budgetMiB>128)throw new IllegalArgumentException("Scan budget must be 1–128 MiB.");
        }catch(Exception e){toast(e.getMessage());return;}
        runAsync("Scanning executable code caves",()->{
            int p=currentPid();List<MapEntry> regions=client.maps(p);
            StringBuilder out=new StringBuilder("Executable padding candidates (00 / 90 / CC)\n");
            long budget=(long)budgetMiB*1024*1024,scanned=0;int found=0;
            for(MapEntry region:regions){
                if((region.prot&4)==0||region.end<=region.start||scanned>=budget)continue;
                long regionLength=Math.min(region.end-region.start,budget-scanned);
                long cursor=region.start,runStart=-1;int runByte=-1,runLength=0;
                while(cursor<region.start+regionLength&&found<200){
                    int n=(int)Math.min(4096,region.start+regionLength-cursor);
                    byte[] data=client.read(p,cursor,n);scanned+=data.length;
                    for(int i=0;i<data.length;i++){
                        int value=data[i]&255;
                        if(value==0x00||value==0x90||value==0xCC){
                            if(runLength>0&&value==runByte)runLength++;
                            else{
                                if(runLength>=minSize){out.append(String.format(Locale.US,"0x%016X  %6d bytes  %02X\n",runStart,runLength,runByte));found++;}
                                runStart=cursor+i;runByte=value;runLength=1;
                            }
                        }else{
                            if(runLength>=minSize){out.append(String.format(Locale.US,"0x%016X  %6d bytes  %02X\n",runStart,runLength,runByte));found++;}
                            runStart=-1;runByte=-1;runLength=0;
                        }
                        if(found>=200)break;
                    }
                    cursor+=data.length;
                    if(data.length==0)break;
                }
                if(runLength>=minSize&&found<200){out.append(String.format(Locale.US,"0x%016X  %6d bytes  %02X\n",runStart,runLength,runByte));found++;}
            }
            out.append("\nScanned: ").append(scanned/1048576).append(" MiB / ").append(budgetMiB).append(" MiB")
               .append("\nCandidates shown: ").append(found);
            if(scanned>=budget)out.append("\nScan budget reached; remaining executable regions were skipped.");
            if(found==0)out.append("\nNo matching padding runs found.");
            return out.toString();
        },caveResult::setText);
    }


    private void startDebugEventListener(){
        try{
            stopDebugEventListener();
            ServerSocket server=new ServerSocket();server.setReuseAddress(true);
            server.bind(new InetSocketAddress(755));server.setSoTimeout(1000);
            debugEventServer=server;debugEventListening=true;
            debugEventResult.setText("Listening on TCP 755. Attach the debugger now; phone and console must be mutually reachable.");
            debugEventWorker.execute(()->{
                while(debugEventListening){
                    try{
                        Socket peer=server.accept();peer.setSoTimeout(5000);
                        try{
                            DataInputStream input=new DataInputStream(new BufferedInputStream(peer.getInputStream()));
                            byte[] packet=new byte[1184];input.readFully(packet);
                            ByteBuffer b=ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
                            long lwp=Integer.toUnsignedLong(b.getInt()),status=Integer.toUnsignedLong(b.getInt());
                            String name=new String(packet,8,40,StandardCharsets.UTF_8).replace(String.valueOf((char)0),"").trim();
                            int regOff=48;
                            long rbp=ByteBuffer.wrap(packet,regOff+80,8).order(ByteOrder.LITTLE_ENDIAN).getLong();
                            long rax=ByteBuffer.wrap(packet,regOff+112,8).order(ByteOrder.LITTLE_ENDIAN).getLong();
                            long rip=ByteBuffer.wrap(packet,regOff+136,8).order(ByteOrder.LITTLE_ENDIAN).getLong();
                            long rflags=ByteBuffer.wrap(packet,regOff+152,8).order(ByteOrder.LITTLE_ENDIAN).getLong();
                            long rsp=ByteBuffer.wrap(packet,regOff+160,8).order(ByteOrder.LITTLE_ENDIAN).getLong();
                            long dr6=ByteBuffer.wrap(packet,0x420+6*8,8).order(ByteOrder.LITTLE_ENDIAN).getLong();
                            int mxcsr=ByteBuffer.wrap(packet,0xE0+0x18,4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                            StringBuilder hits=new StringBuilder();
                            for(int slot=0;slot<4;slot++)if((dr6&(1L<<slot))!=0){if(hits.length()>0)hits.append(", ");hits.append("watchpoint ").append(slot);}
                            if((dr6&(1L<<14))!=0){if(hits.length()>0)hits.append(", ");hits.append("single-step");}
                            int signal=((status&0xFF)==0x7F)?(int)((status>>8)&0xFF):(int)(status&0x7F);
                            String xmm0=toHex(Arrays.copyOfRange(packet,0xE0+0xA0,0xE0+0xA0+16));
                            String event="DEBUG EVENT RECEIVED\\nThread/LWP: "+lwp+" ("+name+")\\nWait status: 0x"+Long.toHexString(status).toUpperCase(Locale.US)
                                +"  •  Signal: "+signal+"\\nRIP: 0x"+String.format(Locale.US,"%016X",rip)+"\\nRBP: 0x"+String.format(Locale.US,"%016X",rbp)
                                +"\\nRSP: 0x"+String.format(Locale.US,"%016X",rsp)+"\\nRAX: 0x"+String.format(Locale.US,"%016X",rax)
                                +"\\nRFLAGS: 0x"+String.format(Locale.US,"%016X",rflags)+"\\nDR6: 0x"+String.format(Locale.US,"%016X",dr6)
                                +"\\nStop reason: "+(hits.length()==0?"signal / debugger stop":hits)
                                +"\\nMXCSR: 0x"+String.format(Locale.US,"%08X",mxcsr)+"\\nXMM0: "+xmm0;
                            runOnUiThread(()->{if(debugEventResult!=null)debugEventResult.setText(event);if(threadId!=null)threadId.setText(String.valueOf(lwp));});
                        }catch(Exception e){runOnUiThread(()->debugEventResult.setText("Debug event packet error: "+e.getMessage()));}
                        finally{try{peer.close();}catch(Exception ignored){}}
                    }catch(SocketTimeoutException ignored){}
                    catch(SocketException e){if(debugEventListening)runOnUiThread(()->debugEventResult.setText("Event listener socket error: "+e.getMessage()));break;}
                    catch(Exception e){if(debugEventListening)runOnUiThread(()->debugEventResult.setText("Event listener error: "+e.getMessage()));}
                }
            });
        }catch(Exception e){debugEventListening=false;debugEventResult.setText("Could not bind TCP 755: "+e.getMessage());}
    }
    private void stopDebugEventListener(){
        debugEventListening=false;
        if(debugEventServer!=null){try{debugEventServer.close();}catch(Exception ignored){}debugEventServer=null;}
    }
    private void debugAttach(boolean attach) {
        if(attach)startDebugEventListener(); else stopDebugEventListener();
        if (!connected) { toast("Connect to the PS5 first."); return; }
        runAsync(attach ? "Attaching debugger" : "Detaching debugger", () -> {
            client.debugAttach(currentPid(), attach);
            return attach ? "Debugger attached. TCP 755 listener is active for asynchronous event packets." : "Debugger detached.";
        }, debugResult::setText);
    }

    private void setBreakpoint(boolean enabled) {
        if (!connected) { toast("Connect to the PS5 first."); return; }
        runAsync(enabled ? "Setting breakpoint" : "Clearing breakpoint", () -> {
            int slot = Integer.parseInt(breakpointIndex.getText().toString().trim());
            long address = resolveAddress(breakpointAddress.getText().toString().trim(), currentPid());
            client.breakpoint(slot, enabled, address);
            return (enabled ? "Breakpoint set" : "Breakpoint cleared") + " at slot " + slot + " / 0x" + Long.toHexString(address);
        }, debugResult::setText);
    }

    private void setWatchpoint(boolean enabled) {
        if (!connected) { toast("Connect to the PS5 first."); return; }
        runAsync(enabled ? "Setting hardware watchpoint" : "Clearing watchpoint", () -> {
            int slot = Integer.parseInt(watchIndex.getText().toString().trim());
            long address = resolveAddress(watchAddress.getText().toString().trim(), currentPid());
            int[] lengths={1,2,4,8};
            int length=enabled?lengths[watchLengthSpinner.getSelectedItemPosition()]:0;
            int mode=enabled?(watchModeSpinner.getSelectedItemPosition()==0?1:3):0;
            if(enabled && Long.remainderUnsigned(address,length)!=0)
                throw new IllegalArgumentException("Watch address must be aligned to the selected "+length+"-byte size.");
            client.watchpoint(slot, enabled, address, length, mode);
            return enabled?(length+"-byte "+(mode==1?"write":"read/write")+" hardware watchpoint set at slot "+slot)
                :"Hardware watchpoint cleared at slot "+slot;
        }, debugResult::setText);
    }

    private void listThreads() {
        if (!connected) { toast("Connect to the PS5 first."); return; }
        runAsync("Listing threads", () -> {
            List<Integer> ids = client.threadList();
            StringBuilder b = new StringBuilder("Threads/LWPs: ").append(ids.size()).append("\\n");
            for (int id : ids) b.append(id).append("\\n");
            return b.toString();
        }, debugResult::setText);
    }

    private void readRegisters() {
        if (!connected) { toast("Connect to the PS5 first."); return; }
        runAsync("Reading registers", () -> {
            int tid = Integer.parseInt(threadId.getText().toString().trim());
            byte[] r = client.getRegisters(tid);
            lastGeneralRegisters=r.clone();lastRegisterTid=tid;
            String[] names={"r15","r14","r13","r12","r11","r10","r9","r8","rdi","rsi","rbp","rbx","rdx","rcx","rax","trapno","fs","gs","err","es","ds","rip","cs","rflags","rsp","ss"};
            int[] offsets={0,8,16,24,32,40,48,56,64,72,80,88,96,104,112,120,124,126,128,132,134,136,144,152,160,168};
            int[] widths={8,8,8,8,8,8,8,8,8,8,8,8,8,8,8,4,2,2,4,2,2,8,8,8,8,8};
            StringBuilder b=new StringBuilder("FreeBSD amd64 general register set\\n");
            ByteBuffer data=ByteBuffer.wrap(r).order(ByteOrder.LITTLE_ENDIAN);
            for(int i=0;i<names.length;i++){
                String value;
                if(widths[i]==8)value=String.format(Locale.US,"%016X",data.getLong(offsets[i]));
                else if(widths[i]==4)value=String.format(Locale.US,"%08X",data.getInt(offsets[i]));
                else value=String.format(Locale.US,"%04X",data.getShort(offsets[i])&0xFFFF);
                b.append(String.format(Locale.US,"%-7s %s\\n",names[i],value));
            }
            return b.toString();
        }, debugResult::setText);
    }

    private static int registerOffset(String name){
        String[] names={"r15","r14","r13","r12","r11","r10","r9","r8","rdi","rsi","rbp","rbx","rdx","rcx","rax","rip","rflags","rsp"};
        int[] offsets={0,8,16,24,32,40,48,56,64,72,80,88,96,104,112,136,152,160};
        for(int i=0;i<names.length;i++)if(names[i].equalsIgnoreCase(name.trim()))return offsets[i];
        return -1;
    }
    private void writeGeneralRegister(){
        if(!connected){toast("Connect to the PS5 first.");return;}
        runAsync("Writing general register with rollback verification",()->{
            int tid=Integer.parseInt(threadId.getText().toString().trim());
            String name=registerNameInput.getText().toString().trim();
            int offset=registerOffset(name);if(offset<0)throw new IllegalArgumentException("Unsupported register. Use a 64-bit GP register name.");
            String raw=registerValueInput.getText().toString().trim();
            long value=raw.startsWith("0x")||raw.startsWith("0X")?Long.parseUnsignedLong(raw.substring(2),16):Long.parseLong(raw);
            byte[] before=client.getRegisters(tid),updated=before.clone();
            ByteBuffer.wrap(updated).order(ByteOrder.LITTLE_ENDIAN).putLong(offset,value);
            try{
                client.setRegisters(tid,updated);
                byte[] check=client.getRegisters(tid);
                if(!Arrays.equals(updated,check))throw new IOException("Register read-back mismatch.");
                lastGeneralRegisters=check.clone();lastRegisterTid=tid;
                return name.toUpperCase(Locale.US)+" updated and verified: 0x"+Long.toHexString(value).toUpperCase(Locale.US);
            }catch(Exception failure){
                try{client.setRegisters(tid,before);if(!Arrays.equals(before,client.getRegisters(tid)))throw new IOException("register rollback mismatch");}
                catch(Exception rollback){throw new IOException("Register write failed and rollback could not be verified: "+rollback.getMessage(),failure);}
                throw new IOException("Register write failed; original register state restored: "+failure.getMessage(),failure);
            }
        },debugResult::setText);
    }
    private void readFpuRegisters(){
        if(!connected){toast("Connect to the PS5 first.");return;}
        runAsync("Reading FPU/XMM/YMM state",()->{
            int tid=Integer.parseInt(threadId.getText().toString().trim());
            byte[] state=client.getFpuRegisters(tid);lastFpuRegisters=state.clone();
            int mxcsr=ByteBuffer.wrap(state,0x18,4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            byte[] xmm0=Arrays.copyOfRange(state,0xA0,0xB0);
            return "FPU/XSTATE image: "+state.length+" bytes\\nMXCSR: 0x"+String.format(Locale.US,"%08X",mxcsr)+"\\nXMM0: "+toHex(xmm0)
                +"\\nYMM0 upper: "+toHex(Arrays.copyOfRange(state,0x240,0x250))+"\\nFull snapshot retained for safe XMM0 edits.";
        },value->{debugResult.setText(value);fpuXmm0Input.setText(toHex(Arrays.copyOfRange(lastFpuRegisters,0xA0,0xB0)));});
    }
    private void writeXmm0(){
        if(!connected){toast("Connect to the PS5 first.");return;}
        runAsync("Writing XMM0 with full xstate rollback",()->{
            int tid=Integer.parseInt(threadId.getText().toString().trim());
            byte[] value=parseHex(fpuXmm0Input.getText().toString());
            if(value.length!=16)throw new IllegalArgumentException("XMM0 requires exactly 16 bytes (32 hex digits).");
            byte[] before=client.getFpuRegisters(tid),updated=before.clone();
            System.arraycopy(value,0,updated,0xA0,16);
            try{
                client.setFpuRegisters(tid,updated);
                byte[] check=client.getFpuRegisters(tid);
                if(!Arrays.equals(updated,check))throw new IOException("FPU/XMM read-back mismatch.");
                lastFpuRegisters=check.clone();return "XMM0 written and verified. Other FPU/YMM state was preserved.";
            }catch(Exception failure){
                try{client.setFpuRegisters(tid,before);if(!Arrays.equals(before,client.getFpuRegisters(tid)))throw new IOException("FPU rollback mismatch");}
                catch(Exception rollback){throw new IOException("XMM0 write failed and xstate rollback could not be verified: "+rollback.getMessage(),failure);}
                throw new IOException("XMM0 write failed; original xstate restored: "+failure.getMessage(),failure);
            }
        },debugResult::setText);
    }

    private void allocateRemoteBuffer(){
        if(!connected){toast("Connect to the PS5 first.");return;}
        if(allocatedBufferAddress!=0){toast("Free the existing remote buffer first.");return;}
        runAsync("Allocating remote process buffer",()->{
            int size=Integer.parseInt(remoteBufferSize.getText().toString().trim());
            if(size<1||size>1024*1024)throw new IllegalArgumentException("Buffer size must be 1 byte–1 MiB.");
            int targetPid=currentPid();
            long address=client.alloc(targetPid,size);
            allocatedBufferAddress=address;allocatedBufferLength=size;allocatedBufferPid=targetPid;
            return "Allocated "+size+" bytes at 0x"+Long.toHexString(address).toUpperCase(Locale.US);
        },value->remoteBufferInfo.setText(value));
    }
    private void writeRemoteBuffer(){
        if(!connected){toast("Connect to the PS5 first.");return;}
        if(allocatedBufferAddress==0||allocatedBufferLength<=0){toast("Allocate a buffer first.");return;}
        runAsync("Writing remote buffer with rollback verification",()->{
            byte[] data=parseHex(remoteBufferBytes.getText().toString());
            if(data.length==0||data.length>allocatedBufferLength)throw new IllegalArgumentException("Data must be 1 byte through the allocated buffer size.");
            int p=allocatedBufferPid;if(p<=0)throw new IOException("The owning process session is no longer available.");
            byte[] before=client.read(p,allocatedBufferAddress,data.length);
            try{
                client.write(p,allocatedBufferAddress,data);
                if(!Arrays.equals(data,client.read(p,allocatedBufferAddress,data.length)))throw new IOException("Buffer read-back mismatch.");
                return "Wrote and verified "+data.length+" bytes at 0x"+Long.toHexString(allocatedBufferAddress).toUpperCase(Locale.US);
            }catch(Exception failure){
                try{client.write(p,allocatedBufferAddress,before);if(!Arrays.equals(before,client.read(p,allocatedBufferAddress,before.length)))throw new IOException("buffer rollback mismatch");}
                catch(Exception rollback){throw new IOException("Buffer write failed and rollback could not be verified: "+rollback.getMessage(),failure);}
                throw new IOException("Buffer write failed; original bytes restored: "+failure.getMessage(),failure);
            }
        },remoteBufferInfo::setText);
    }
    private void freeRemoteBuffer(){
        if(!connected){toast("Connect to the PS5 first.");return;}
        if(allocatedBufferAddress==0||allocatedBufferLength<=0){toast("No session-owned remote buffer to free.");return;}
        runAsync("Freeing remote process buffer",()->{
            long address=allocatedBufferAddress;int size=allocatedBufferLength;int ownerPid=allocatedBufferPid;
            if(ownerPid<=0)throw new IOException("The owning process ID is not available.");
            client.free(ownerPid,address,size);
            allocatedBufferAddress=0;allocatedBufferLength=0;allocatedBufferPid=-1;
            return "Freed remote buffer at 0x"+Long.toHexString(address).toUpperCase(Locale.US)+" ("+size+" bytes).";
        },remoteBufferInfo::setText);
    }

    private void callRemoteFunction(){
        if(!connected){toast("Connect to the PS5 first.");return;}
        runAsync("Calling remote function through safe trampoline",()->{
            int p=currentPid();long target=resolveAddress(remoteCallAddress.getText().toString().trim(),p);
            String[] parts=remoteCallArguments.getText().toString().trim().split("[,\\s]+");
            if(parts.length>6)throw new IllegalArgumentException("Enter no more than six arguments.");
            long[] args=new long[6];
            for(int i=0;i<parts.length;i++){
                String value=parts[i].trim();
                args[i]=value.startsWith("0x")||value.startsWith("0X")?Long.parseUnsignedLong(value.substring(2),16):Long.parseLong(value);
            }
            boolean executable=false;
            for(MapEntry map:client.maps(p))if((map.prot&4)!=0&&Long.compareUnsigned(target,map.start)>=0&&Long.compareUnsigned(target,map.end)<0){executable=true;break;}
            if(!executable)throw new IOException("Function address is not inside an executable memory map.");
            long result=client.callSafe(p,target,args);
            return "Remote call completed once.\\nTarget: 0x"+Long.toHexString(target).toUpperCase(Locale.US)
                +"\\nRAX: 0x"+Long.toHexString(result).toUpperCase(Locale.US);
        },debugResult::setText);
    }

    private void debugStep() {
        if (!connected) { toast("Connect to the PS5 first."); return; }
        runAsync("Single stepping process", () -> { client.debugStep(); return "Single-step command accepted."; }, debugResult::setText);
    }
    private void debugStepThread(){
        if(!connected){toast("Connect to the PS5 first.");return;}
        runAsync("Single stepping selected thread",()->{
            int tid=Integer.parseInt(threadId.getText().toString().trim());
            client.debugStepThread(tid);return "Single-step command accepted for LWP "+tid+".";
        },debugResult::setText);
    }
    private void setProcessPaused(boolean pause){
        if(!connected){toast("Connect to the PS5 first.");return;}
        runAsync(pause?"Pausing process":"Resuming process",()->{
            client.debugContinue(pause?1:0);
            return pause?"Process stop (SIGSTOP) accepted.":"Process resume accepted.";
        },debugResult::setText);
    }

    private void readCallStack() {
        if(!connected){toast("Connect to the PS5 first.");return;}
        final String rbpText=stackRbp.getText().toString().trim(),rspText=stackRsp.getText().toString().trim();
        final int depth;
        try{depth=Integer.parseInt(stackDepth.getText().toString().trim());if(depth<1||depth>64)throw new IllegalArgumentException("Frame count must be 1–64.");}
        catch(Exception e){toast(e.getMessage());return;}
        runAsync("Reading call stack",()->{
            int p=currentPid();long rbp,rsp;
            if(rbpText.isEmpty()||rspText.isEmpty()){
                int tid=Integer.parseInt(threadId.getText().toString().trim());
                byte[] regs=client.getRegisters(tid);
                if(regs.length<120)throw new IOException("Register response is too short to read RBP/RSP.");
                long liveRbp=ByteBuffer.wrap(regs,80,8).order(ByteOrder.LITTLE_ENDIAN).getLong();
                long liveRsp=ByteBuffer.wrap(regs,112,8).order(ByteOrder.LITTLE_ENDIAN).getLong();
                rbp=rbpText.isEmpty()?liveRbp:parseAddress(rbpText);
                rsp=rspText.isEmpty()?liveRsp:parseAddress(rspText);
            }else{rbp=parseAddress(rbpText);rsp=parseAddress(rspText);}
            byte[] raw=client.readStack(p,rbp,rsp,depth);
            if(raw.length<4)throw new IOException("Call-stack payload is incomplete.");
            ByteBuffer b=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            long count=Integer.toUnsignedLong(b.getInt());StringBuilder out=new StringBuilder("Frames returned: ").append(Math.min(count,depth)).append("\n");
            for(int i=0;i<count&&i<depth;i++){
                if(b.remaining()<44)throw new IOException("Truncated call-stack frame header at frame "+i+".");
                long fRbp=b.getLong(),fRsp=b.getLong(),saved=b.getLong(),ret=b.getLong();
                long flags=Integer.toUnsignedLong(b.getInt()),locLen=Integer.toUnsignedLong(b.getInt()),codeLen=Integer.toUnsignedLong(b.getInt());
                if(locLen>b.remaining()||codeLen>b.remaining()-locLen)throw new IOException("Invalid frame payload lengths at frame "+i+".");
                byte[] loc=new byte[(int)locLen];b.get(loc);byte[] code=new byte[(int)codeLen];b.get(code);
                String location=new String(loc,StandardCharsets.UTF_8).replace(String.valueOf((char)0),"").trim();
                out.append("\n#").append(i).append("  RIP 0x").append(String.format(Locale.US,"%016X",ret))
                   .append("\n   RBP 0x").append(String.format(Locale.US,"%016X",fRbp))
                   .append("  RSP 0x").append(String.format(Locale.US,"%016X",fRsp))
                   .append("\n   saved RBP 0x").append(String.format(Locale.US,"%016X",saved))
                   .append("  flags 0x").append(Long.toHexString(flags).toUpperCase(Locale.US));
                if(!location.isEmpty())out.append("\n   ").append(location);
                if(code.length>0)out.append("\n   code: ").append(toHex(Arrays.copyOf(code,Math.min(code.length,24))));
                out.append('\n');
            }
            if(count==0)out.append("No frame-pointer chain found; the title may omit frame pointers or the thread may not be paused.");
            return out.toString();
        },stackResult::setText);
    }

    private static long parseAddress(String value) {
        String s=value.trim();if(s.startsWith("0x")||s.startsWith("0X"))return Long.parseUnsignedLong(s.substring(2),16);
        return Long.decode(s);
    }

    private void createGameFile() {
        String name = gameName.getText().toString().trim();
        if (name.isEmpty()) { toast("Enter a game name."); return; }
        try {
            if (gameFile == null) gameFile = new JSONObject();
            gameFile.put("format", "ps5cheat/1");
            gameFile.put("game", name);
            gameFile.put("title_id", titleId.getText().toString().trim().toUpperCase(Locale.US));
            gameFile.put("version", gameVersion.getText().toString().trim());
            gameFile.put("process", "eboot.bin");
            if (!gameFile.has("cheats")) gameFile.put("cheats", new JSONArray());
            refreshCheatList();
            log("Game file ready: " + name);
        } catch (JSONException e) { toast(e.getMessage()); }
    }

    private void addPatchCheat() {
        if (gameFile == null) createGameFile();
        if (gameFile == null) return;
        String name = cheatName.getText().toString().trim();
        String address = cheatAddress.getText().toString().trim();
        try {
            byte[] patch = parseHex(cheatBytes.getText().toString());
            if (name.isEmpty() || address.isEmpty() || patch.length == 0) throw new IllegalArgumentException("Enter cheat name, address and patch bytes.");
            JSONObject step = new JSONObject();
            step.put("op", "patch");
            step.put("addr", address);
            step.put("type", "hex");
            step.put("value", toHex(patch));
            JSONObject cheat = new JSONObject();
            cheat.put("name", name);
            cheat.put("mode", "toggle");
            cheat.put("steps", new JSONArray().put(step));
            gameFile.getJSONArray("cheats").put(cheat);
            refreshCheatList();
            log("Added patch cheat: " + name);
        } catch (Exception e) { toast(e.getMessage()); }
    }

    private void importJson() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_IMPORT);
    }




    private static final String MC4_KEY="304c6528f659c766110239a51cl5dd9c";
    private static final String MC4_IV="u@}kzW2u[u(8DWar";
    private String decryptMc4(String encoded)throws Exception{
        byte[] cipherBytes=android.util.Base64.decode(encoded.replaceAll("\\s+",""),android.util.Base64.DEFAULT);
        Cipher cipher=Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(MC4_KEY.getBytes(StandardCharsets.US_ASCII),"AES"),new IvParameterSpec(MC4_IV.getBytes(StandardCharsets.US_ASCII)));
        byte[] plain=cipher.doFinal(cipherBytes);String xml;
        if(plain.length>=2&&(plain[0]&255)==0xFF&&(plain[1]&255)==0xFE)xml=new String(plain,2,plain.length-2,StandardCharsets.UTF_16LE).trim();
        else if(plain.length>=2&&(plain[0]&255)==0xFE&&(plain[1]&255)==0xFF)xml=new String(plain,2,plain.length-2,StandardCharsets.UTF_16BE).trim();
        else xml=new String(plain,StandardCharsets.UTF_8).trim();
        if(!xml.contains("<Trainer"))xml=xml.replace("&lt;","<").replace("&gt;",">").replace("&quot;","\\\"").replace("&apos;","'");
        if(!xml.contains("<Trainer"))throw new IOException("MC4 did not decrypt to a Trainer XML document.");
        return xml;
    }
    private String encryptMc4(String xml)throws Exception{
        Cipher cipher=Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(MC4_KEY.getBytes(StandardCharsets.US_ASCII),"AES"),new IvParameterSpec(MC4_IV.getBytes(StandardCharsets.US_ASCII)));
        return android.util.Base64.encodeToString(cipher.doFinal(xml.getBytes(StandardCharsets.UTF_8)),android.util.Base64.NO_WRAP);
    }
    private void exportMc4(){
        if(gameFile==null){toast("Create or import a game file first.");return;}
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/plain");
        String id=gameFile.optString("title_id","PS5");i.putExtra(Intent.EXTRA_TITLE,(id.isEmpty()?"PS5-game":id)+".mc4");
        startActivityForResult(i,REQ_EXPORT_MC4);
    }

    private void exportShn(){
        if(gameFile==null){toast("Create or import a game file first.");return;}
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/xml");
        String id=gameFile.optString("title_id","PS5");i.putExtra(Intent.EXTRA_TITLE,(id.isEmpty()?"PS5-game":id)+".shn");
        startActivityForResult(i,REQ_EXPORT_SHN);
    }
    private String buildShnXml()throws Exception{
        JSONObject gold=buildGoldHenJson();StringWriter writer=new StringWriter();XmlSerializer x=Xml.newSerializer();x.setOutput(writer);
        x.startDocument("UTF-8",true);x.startTag(null,"Trainer");
        x.attribute(null,"Game",gold.optString("name","game"));x.attribute(null,"Cusa",gold.optString("id",""));
        x.attribute(null,"Version",gold.optString("version",""));x.attribute(null,"Process",gold.optString("process","eboot.bin"));
        JSONArray mods=gold.getJSONArray("mods");
        for(int i=0;i<mods.length();i++){
            JSONObject mod=mods.getJSONObject(i);x.startTag(null,"Cheat");x.attribute(null,"Text",mod.optString("name","Cheat"));
            JSONArray memory=mod.getJSONArray("memory");
            for(int j=0;j<memory.length();j++){
                JSONObject line=memory.getJSONObject(j);x.startTag(null,"Cheatline");
                x.startTag(null,"Section");x.text("0");x.endTag(null,"Section");
                x.startTag(null,"Offset");x.text(line.optString("offset","0"));x.endTag(null,"Offset");
                x.startTag(null,"ValueOn");x.text(line.optString("on",""));x.endTag(null,"ValueOn");
                x.startTag(null,"ValueOff");x.text("");x.endTag(null,"ValueOff");
                if(line.optBoolean("absolute",false)){x.startTag(null,"Absolute");x.text("true");x.endTag(null,"Absolute");}
                x.endTag(null,"Cheatline");
            }
            x.endTag(null,"Cheat");
        }
        x.endTag(null,"Trainer");x.endDocument();return writer.toString();
    }

    private void exportGoldHenJson(){
        if(gameFile==null){toast("Create or import a game file first.");return;}
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");
        String id=gameFile.optString("title_id","PS5");i.putExtra(Intent.EXTRA_TITLE,(id.isEmpty()?"PS5-game":id)+"_GoldHEN.json");
        startActivityForResult(i,REQ_EXPORT_GOLDHEN);
    }
    private JSONObject buildGoldHenJson()throws Exception{
        JSONObject out=new JSONObject();out.put("name",gameFile.optString("game","game"));out.put("id",gameFile.optString("title_id",""));
        out.put("version",gameFile.optString("version",""));out.put("process",gameFile.optString("process","eboot.bin"));
        JSONArray mods=new JSONArray(),cheats=gameFile.optJSONArray("cheats");
        if(cheats==null)throw new IOException("Game file has no cheats array.");
        for(int i=0;i<cheats.length();i++){
            JSONObject cheat=cheats.getJSONObject(i);JSONArray steps=cheat.optJSONArray("steps");
            if(steps==null||steps.length()==0)throw new IOException("Cannot export '"+cheat.optString("name")+"': no steps.");
            JSONArray memory=new JSONArray();
            for(int j=0;j<steps.length();j++){
                JSONObject step=steps.getJSONObject(j);
                if(!"patch".equalsIgnoreCase(step.optString("op"))||!"hex".equalsIgnoreCase(step.optString("type","hex")))
                    throw new IOException("Cannot export '"+cheat.optString("name")+"': only fixed hex patch steps are supported.");
                String addr=step.optString("addr","").trim();long off;boolean absolute=false;
                if(addr.matches("(?i)0x[0-9a-f]+")){off=Long.parseUnsignedLong(addr.substring(2),16);absolute=true;}
                else{
                    java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)^module\\((main|eboot\\.bin|executable)\\)\\s*\\+\\s*(0x[0-9a-f]+|[0-9]+)$").matcher(addr);
                    if(!m.matches())throw new IOException("Cannot export '"+cheat.optString("name")+"': address is not a fixed main-module offset ("+addr+").");
                    String v=m.group(2);off=v.toLowerCase(Locale.US).startsWith("0x")?Long.parseUnsignedLong(v.substring(2),16):Long.parseUnsignedLong(v);
                }
                byte[] bytes=parseHex(step.optString("value",""));if(bytes.length==0)throw new IOException("Empty patch in '"+cheat.optString("name")+"'.");
                JSONObject line=new JSONObject().put("offset",Long.toHexString(off).toUpperCase(Locale.US)).put("on",toHex(bytes));
                if(absolute)line.put("absolute",true);
                memory.put(line);
            }
            mods.put(new JSONObject().put("name",cheat.optString("name","Cheat")).put("type","checkbox").put("memory",memory));
        }
        out.put("mods",mods);out.put("credits",new JSONArray());return out;
    }

    private void exportJson() {
        if (gameFile == null) { toast("Create or import a game file first."); return; }
        String id = gameFile.optString("title_id", "PS5");
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, (id.isEmpty() ? "PS5-game" : id) + ".json");
        startActivityForResult(i, REQ_EXPORT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == REQ_IMPORT_ADDRESS_TABLE) {
                JSONObject imported = new JSONObject(readUri(uri).trim());
                if (!"ps5-address-table/1".equals(imported.optString("format")) || imported.optJSONArray("entries") == null)
                    throw new JSONException("Unsupported address table. Expected ps5-address-table/1.");
                JSONArray entries = imported.getJSONArray("entries");
                List<AddressEntry> loaded = new ArrayList<>();
                if (entries.length() > 10000) throw new IllegalArgumentException("Address table exceeds the 10,000-entry safety limit.");
                for (int n=0;n<entries.length();n++) {
                    JSONObject item=entries.optJSONObject(n);
                    if(item==null)throw new JSONException("Address entry "+(n+1)+" is not an object.");
                    String address=item.optString("address","").trim();
                    String bytes=toHex(parseHex(item.optString("bytes","")));
                    int type=item.optInt("type",0);
                    if(address.isEmpty()||bytes.isEmpty()||type<0||type>10)throw new IllegalArgumentException("Invalid address entry "+(n+1)+".");
                    int byteLength=parseHex(bytes).length;
                    if(byteLength>4096)throw new IllegalArgumentException("Entry "+(n+1)+" exceeds 4096 bytes.");
                    int expectedLength=type==1||type==2?1:type==3||type==4?2:type==5||type==6||type==9?4:type==7||type==8||type==10?8:byteLength;
                    if(byteLength!=expectedLength)throw new IllegalArgumentException("Entry "+(n+1)+" has an invalid byte length for "+addressTypeName(type)+".");
                    // Never activate imported freeze entries automatically: imported addresses
                    // may belong to another process/session. User can freeze them explicitly.
                    loaded.add(new AddressEntry(item.optString("name","Entry "+(n+1)),address,bytes,type,false));
                }
                addressEntries.clear();addressEntries.addAll(loaded);saveAddressEntries();renderAddressEntries();
                addressHandler.removeCallbacks(addressFreezeTick);if(hasFrozenAddresses()&&connected)addressHandler.post(addressFreezeTick);
                log("Loaded "+loaded.size()+" address entries from file. Freeze flags were reset for safety; freeze entries manually after checking the process.");
            } else if (requestCode == REQ_IMPORT_HEX_PATCH) {
                byte[] patch;
                try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream buffer=new ByteArrayOutputStream()){
                    if(in==null)throw new IOException("Could not open patch file.");
                    byte[] chunk=new byte[1024];int n;while((n=in.read(chunk))!=-1){if(buffer.size()+n>4096)throw new IllegalArgumentException("Patch file exceeds the 4096-byte page limit.");buffer.write(chunk,0,n);}
                    patch=buffer.toByteArray();
                }
                if(patch.length!=hexEditorOriginal.length)throw new IllegalArgumentException("Patch file must be exactly "+hexEditorOriginal.length+" bytes to match the loaded page.");
                hexEditorInput.setText(toHex(patch));
                hexEditorInfo.setText("Patch file loaded ("+patch.length+" bytes). Original snapshot is unchanged; press WRITE + VERIFY to apply.");
                log("Loaded a "+patch.length+"-byte patch file into the hex editor; no memory was written.");
            } else if (requestCode == REQ_EXPORT_HEX_PAGE) {
                byte[] bytes=parseHex(hexEditorInput.getText().toString());
                if(bytes.length==0||bytes.length>4096)throw new IllegalArgumentException("Export page must contain 1–4096 bytes.");
                try(OutputStream out=getContentResolver().openOutputStream(uri)){
                    if(out==null)throw new IOException("Could not open destination.");
                    out.write(bytes);
                }
                log("Exported "+bytes.length+" raw memory bytes from 0x"+Long.toHexString(hexEditorBase).toUpperCase(Locale.US)+".");
            } else if (requestCode == REQ_EXPORT_ADDRESS_TABLE) {
                JSONObject table=new JSONObject();table.put("format","ps5-address-table/1");
                JSONArray entries=new JSONArray();
                for(AddressEntry e:addressEntries){JSONObject item=new JSONObject();item.put("name",e.name);item.put("address",e.address);item.put("bytes",e.bytes);item.put("type",e.type);item.put("frozen",e.frozen);entries.put(item);}
                table.put("entries",entries);
                try(OutputStream out=getContentResolver().openOutputStream(uri)){
                    if(out==null)throw new IOException("Could not open destination.");
                    out.write(table.toString(2).getBytes(StandardCharsets.UTF_8));
                }
                log("Saved "+addressEntries.size()+" address entries to JSON.");
            } else if (requestCode == REQ_IMPORT) {
                String json = readUri(uri).trim();
                if(json.startsWith("<")&&json.contains("<Trainer")){
                    gameFile=convertShnXml(json);
                    gameName.setText(gameFile.optString("game"));titleId.setText(gameFile.optString("title_id"));gameVersion.setText(gameFile.optString("version"));
                    refreshCheatList();log("Imported "+gameFile.getJSONArray("cheats").length()+" SHN cheat(s).");
                }else if(json.startsWith("{")){
                    JSONObject imported = new JSONObject(json);
                    if(imported.has("game")&&imported.has("cheats")&&"ps5cheat/1".equals(imported.optString("format"))) gameFile=imported;
                    else if(imported.has("mods")) gameFile=convertGoldHenJson(imported);
                    else throw new JSONException("Unsupported JSON. Expected ps5cheat/1 or GoldHEN mods JSON.");
                    gameName.setText(gameFile.optString("game"));titleId.setText(gameFile.optString("title_id"));gameVersion.setText(gameFile.optString("version"));
                    refreshCheatList();log("Imported "+gameFile.getJSONArray("cheats").length()+" supported cheat(s).");
                }else{
                    gameFile=convertShnXml(decryptMc4(json));
                    gameName.setText(gameFile.optString("game"));titleId.setText(gameFile.optString("title_id"));gameVersion.setText(gameFile.optString("version"));
                    refreshCheatList();log("Decrypted and imported "+gameFile.getJSONArray("cheats").length()+" MC4 cheat(s).");
                }
            } else if (requestCode == REQ_EXPORT_MC4) {
                String mc4=encryptMc4(buildShnXml());
                try(OutputStream out=getContentResolver().openOutputStream(uri)){
                    if(out==null)throw new IOException("Could not open destination.");
                    out.write(mc4.getBytes(StandardCharsets.US_ASCII));
                }
                log("Exported encrypted MC4 trainer.");
            } else if (requestCode == REQ_EXPORT_SHN) {
                String shn=buildShnXml();
                try(OutputStream out=getContentResolver().openOutputStream(uri)){
                    if(out==null)throw new IOException("Could not open destination.");
                    out.write(shn.getBytes(StandardCharsets.UTF_8));
                }
                log("Exported SHN XML.");
            } else if (requestCode == REQ_EXPORT_GOLDHEN) {
                JSONObject goldhen=buildGoldHenJson();
                try(OutputStream out=getContentResolver().openOutputStream(uri)){
                    if(out==null)throw new IOException("Could not open destination.");
                    out.write(goldhen.toString(2).getBytes(StandardCharsets.UTF_8));
                }
                log("Exported GoldHEN JSON.");
            } else if (requestCode == REQ_EXPORT) {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IOException("Could not open destination.");
                    out.write(gameFile.toString(2).getBytes(StandardCharsets.UTF_8));
                }
                log("Exported game cheat JSON.");
            }
        } catch (Exception e) {
            toast("File error: " + e.getMessage());
        }
    }

    private String readUri(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IOException("Could not open file.");
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            byte[] data=out.toByteArray();
            if(data.length>=2&&(data[0]&255)==0xFF&&(data[1]&255)==0xFE)return new String(data,2,data.length-2,StandardCharsets.UTF_16LE);
            if(data.length>=2&&(data[0]&255)==0xFE&&(data[1]&255)==0xFF)return new String(data,2,data.length-2,StandardCharsets.UTF_16BE);
            if(data.length>3&&data[1]==0&&data[3]==0)return new String(data,StandardCharsets.UTF_16LE);
            if(data.length>=3&&(data[0]&255)==0xEF&&(data[1]&255)==0xBB&&(data[2]&255)==0xBF)return new String(data,3,data.length-3,StandardCharsets.UTF_8);
            return new String(data,StandardCharsets.UTF_8);
        }
    }



    private JSONObject convertShnXml(String xml)throws Exception{
        XmlPullParser parser=Xml.newPullParser();parser.setInput(new java.io.StringReader(xml));
        JSONObject out=new JSONObject();JSONArray cheats=new JSONArray();
        String game="Imported SHN",title="",version="",process="eboot.bin",cheatName=null,section="0",offset="",on="",absolute="false";
        JSONArray steps=null;int event=parser.getEventType();
        while(event!=XmlPullParser.END_DOCUMENT){
            if(event==XmlPullParser.START_TAG){
                String tag=parser.getName();
                if("Trainer".equalsIgnoreCase(tag)){
                    String v=parser.getAttributeValue(null,"Game");if(v!=null&&!v.isEmpty())game=v;
                    v=parser.getAttributeValue(null,"Cusa");if(v!=null)title=v.toUpperCase(Locale.US);
                    v=parser.getAttributeValue(null,"Version");if(v!=null)version=v;
                    v=parser.getAttributeValue(null,"Process");if(v!=null)process=v;
                }else if("Cheat".equalsIgnoreCase(tag)){
                    cheatName=parser.getAttributeValue(null,"Text");if(cheatName==null||cheatName.isEmpty())cheatName="Imported cheat";
                    steps=new JSONArray();
                }else if("Cheatline".equalsIgnoreCase(tag)){section="0";offset="";on="";absolute="false";}
                else if("Section".equalsIgnoreCase(tag))section=parser.nextText().trim();
                else if("Offset".equalsIgnoreCase(tag))offset=parser.nextText().trim();
                else if("ValueOn".equalsIgnoreCase(tag))on=parser.nextText().trim();
                else if("Absolute".equalsIgnoreCase(tag))absolute=parser.nextText().trim();
            }else if(event==XmlPullParser.END_TAG){
                String tag=parser.getName();
                if("Cheatline".equalsIgnoreCase(tag)&&steps!=null&&!offset.isEmpty()&&!on.isEmpty()){
                    int sec=0;try{String st=section.trim();sec=st.toLowerCase(Locale.US).startsWith("0x")?Integer.parseInt(st.substring(2),16):Integer.parseInt(st);}catch(Exception ignored){}
                    if(sec==0)try{
                        long off=offset.toLowerCase(Locale.US).startsWith("0x")?Long.parseUnsignedLong(offset.substring(2),16):Long.parseUnsignedLong(offset,16);
                        byte[] bytes=parseHex(on);if(bytes.length>0){
                            boolean abs=absolute.equalsIgnoreCase("true")||absolute.equals("1");
                            String addr=abs?"0x"+Long.toHexString(off):"module(main)+0x"+Long.toHexString(off);
                            steps.put(new JSONObject().put("op","patch").put("addr",addr).put("type","hex").put("value",toHex(bytes)));
                        }
                    }catch(Exception ignored){}
                }else if("Cheat".equalsIgnoreCase(tag)&&steps!=null){
                    if(steps.length()>0)cheats.put(new JSONObject().put("name",cheatName).put("mode","toggle").put("steps",steps));
                    cheatName=null;steps=null;
                }
            }
            event=parser.next();
        }
        if(cheats.length()==0)throw new IOException("SHN contains no supported section-0 fixed hex patches.");
        out.put("format","ps5cheat/1").put("game",game).put("title_id",title).put("version",version).put("process",process);
        out.put("notes","Imported from SHN XML. Off bytes are restored from live memory; non-zero sections and unsupported lines are skipped.");
        out.put("cheats",cheats);return out;
    }

    private JSONObject convertGoldHenJson(JSONObject source)throws Exception{
        JSONObject out=new JSONObject();
        out.put("format","ps5cheat/1");
        out.put("game",source.optString("name",source.optString("id","Imported GoldHEN game")));
        out.put("title_id",source.optString("id","").toUpperCase(Locale.US));
        out.put("version",source.optString("version",""));
        out.put("process",source.optString("process","eboot.bin"));
        out.put("notes","Imported from GoldHEN JSON. Off bytes are restored from live memory when toggled off. Non-zero sections are skipped.");
        JSONArray cheats=new JSONArray(),mods=source.optJSONArray("mods");
        if(mods==null)throw new JSONException("GoldHEN JSON has no mods array.");
        int skipped=0;
        for(int i=0;i<mods.length();i++){
            JSONObject mod=mods.optJSONObject(i);if(mod==null)continue;
            JSONArray memory=mod.optJSONArray("memory");if(memory==null||memory.length()==0)continue;
            JSONArray steps=new JSONArray();
            for(int j=0;j<memory.length();j++){
                JSONObject mem=memory.optJSONObject(j);if(mem==null)continue;
                String on=mem.optString("on","").trim(),offset=mem.optString("offset","").trim();
                if(on.isEmpty()||offset.isEmpty())continue;
                String sectionText=mem.optString("section","0").trim();int section=0;
                try{section=sectionText.toLowerCase(Locale.US).startsWith("0x")?Integer.parseInt(sectionText.substring(2),16):Integer.parseInt(sectionText);}catch(Exception ignored){}
                if(section!=0){skipped++;continue;}
                long off;
                try{off=offset.toLowerCase(Locale.US).startsWith("0x")?Long.parseUnsignedLong(offset.substring(2),16):Long.parseUnsignedLong(offset,16);}
                catch(Exception e){skipped++;continue;}
                byte[] bytes;
                try{bytes=parseHex(on);}catch(Exception e){skipped++;continue;}
                if(bytes.length==0){skipped++;continue;}
                boolean absolute=mem.optBoolean("absolute",false);
                String address=absolute?"0x"+Long.toHexString(off):"module(main)+0x"+Long.toHexString(off);
                steps.put(new JSONObject().put("op","patch").put("addr",address).put("type","hex").put("value",toHex(bytes)));
            }
            if(steps.length()==0)continue;
            JSONObject cheat=new JSONObject();
            cheat.put("name",mod.optString("name","Imported cheat "+(i+1)));
            cheat.put("mode","toggle");
            cheat.put("steps",steps);
            cheats.put(cheat);
        }
        if(cheats.length()==0)throw new JSONException("No supported section-0 GoldHEN memory patches found.");
        out.put("cheats",cheats);
        if(skipped>0)out.put("conversion_warnings",skipped+" memory line(s) skipped (unsupported section or invalid offset/hex).");
        return out;
    }

    private void refreshCheatList() {
        cheatList.removeAllViews();
        persistGameFile();
        if (gameFile == null) { cheatStatus.setText("No game file loaded."); return; }
        JSONArray array = gameFile.optJSONArray("cheats");
        int count = array == null ? 0 : array.length();
        cheatStatus.setText(gameFile.optString("game") + "  •  " + gameFile.optString("title_id") + "  •  " + count + " cheat(s)");
        for (int index = 0; index < count; index++) {
            JSONObject c = array.optJSONObject(index);
            if (c == null) continue;
            final int idx = index;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(11), dp(8), dp(11), dp(8));
            row.setBackgroundColor(PANEL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.topMargin = dp(5);
            cheatList.addView(row, rowLp);
            LinearLayout heading = new LinearLayout(this);
            heading.setOrientation(LinearLayout.HORIZONTAL);
            heading.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = text(c.optString("name", "Unnamed cheat"), 14, TEXT, true);
            heading.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            CheckBox available = new CheckBox(this);
            available.setText("Enabled");
            available.setTextColor(TEXT);
            available.setTextSize(12);
            available.setChecked(c.optBoolean("enabled", true));
            heading.addView(available);
            row.addView(heading);
            TextView detail = text(c.optString("mode", "toggle").toUpperCase(Locale.US) + "  •  " +
                    (c.optJSONArray("steps") == null ? 0 : c.optJSONArray("steps").length()) + " step(s)", 11, MUTED, false);
            row.addView(detail);
            LinearLayout primaryActions = new LinearLayout(this);
            primaryActions.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout orderActions = new LinearLayout(this);
            orderActions.setOrientation(LinearLayout.HORIZONTAL);
            boolean runOnce = "once".equalsIgnoreCase(c.optString("mode", "toggle"));
            Button apply = button(enabledCheats.containsKey(idx) ? "RESTORE" : (runOnce ? "RUN ONCE" : "APPLY"), ACCENT);
            apply.setEnabled(enabledCheats.containsKey(idx) || c.optBoolean("enabled", true));
            available.setEnabled(!enabledCheats.containsKey(idx));
            available.setOnCheckedChangeListener((view, checked) -> {
                try {
                    c.put("enabled", checked);
                    persistGameFile();
                    apply.setEnabled(checked);
                    log((checked ? "Enabled " : "Disabled ") + c.optString("name") + " in the cheat library.");
                } catch (JSONException e) { toast("Could not save enabled state: " + e.getMessage()); }
            });
            Button edit = button("EDIT", PANEL);
            Button duplicate = button("DUPLICATE", PANEL);
            Button up = button("MOVE UP", PANEL);
            Button down = button("MOVE DOWN", PANEL);
            Button remove = button("DELETE", PANEL);
            primaryActions.addView(apply, new LinearLayout.LayoutParams(0, dp(44), 2));
            primaryActions.addView(edit, new LinearLayout.LayoutParams(0, dp(44), 1));
            primaryActions.addView(remove, new LinearLayout.LayoutParams(0, dp(44), 1));
            orderActions.addView(duplicate, new LinearLayout.LayoutParams(0, dp(40), 1));
            orderActions.addView(up, new LinearLayout.LayoutParams(0, dp(40), 1));
            orderActions.addView(down, new LinearLayout.LayoutParams(0, dp(40), 1));
            row.addView(primaryActions);
            row.addView(orderActions);
            apply.setOnClickListener(v -> {
                if (!enabledCheats.containsKey(idx) && !c.optBoolean("enabled", true)) {
                    toast("This cheat is disabled in the library. Enable it first.");
                    return;
                }
                toggleCheat(idx, c);
            });
            edit.setOnClickListener(v -> editCheat(idx, c));
            up.setEnabled(idx > 0 && enabledCheats.isEmpty());
            down.setEnabled(idx < count - 1 && enabledCheats.isEmpty());
            remove.setEnabled(enabledCheats.isEmpty());
            edit.setEnabled(enabledCheats.isEmpty());
            duplicate.setEnabled(enabledCheats.isEmpty());
            duplicate.setOnClickListener(v -> duplicateCheat(idx));
            up.setOnClickListener(v -> moveCheat(idx, -1));
            down.setOnClickListener(v -> moveCheat(idx, 1));
            remove.setOnClickListener(v -> deleteCheat(idx));
        }
    }

    private void persistGameFile() {
        if (gameFile == null) return;
        getPreferences(MODE_PRIVATE).edit()
                .putString("saved_game_file", gameFile.toString())
                .apply();
    }

    private void restoreStoredGameFile() {
        String saved = getPreferences(MODE_PRIVATE).getString("saved_game_file", "");
        if (saved == null || saved.trim().isEmpty()) return;
        try {
            JSONObject restored = new JSONObject(saved);
            if (!"ps5cheat/1".equals(restored.optString("format")) ||
                    restored.optJSONArray("cheats") == null) return;
            gameFile = restored;
            gameName.setText(gameFile.optString("game", ""));
            titleId.setText(gameFile.optString("title_id", ""));
            gameVersion.setText(gameFile.optString("version", ""));
            refreshCheatList();
            log("Restored saved game/cheat library from this device.");
        } catch (Exception e) {
            log("Saved cheat library could not be restored: " + e.getMessage());
        }
    }

    private void duplicateCheat(int index) {
        if (gameFile == null || !enabledCheats.isEmpty()) {
            toast("Restore active cheats before duplicating.");
            return;
        }
        JSONArray cheats = gameFile.optJSONArray("cheats");
        if (cheats == null || index < 0 || index >= cheats.length()) return;
        try {
            JSONObject source = cheats.getJSONObject(index);
            JSONObject copy = new JSONObject(source.toString());
            String originalName = source.optString("name", "Cheat");
            copy.put("name", originalName + " Copy");
            cheats.put(index + 1, copy);
            refreshCheatList();
            log("Duplicated cheat: " + originalName);
        } catch (Exception e) {
            toast("Could not duplicate cheat: " + e.getMessage());
        }
    }

    private void editCheat(int index, JSONObject cheat) {
        if (!enabledCheats.isEmpty()) { toast("Restore active cheats before editing."); return; }
        EditText editor = new EditText(this);
        editor.setText(cheat.toString());
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setTextSize(13);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setMinLines(12);
        editor.setMaxLines(22);
        editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        ScrollView container = new ScrollView(this);
        container.setPadding(dp(14), dp(8), dp(14), dp(8));
        container.addView(editor);
        new android.app.AlertDialog.Builder(this).setTitle("Edit complete cheat JSON")
            .setMessage("Edit the full cheat object, including every step, address, type and value. Keep valid JSON.")
            .setView(container).setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE", (dialog, which) -> {
                try {
                    JSONObject updated = new JSONObject(editor.getText().toString());
                    String name = updated.optString("name", "").trim();
                    String mode = updated.optString("mode", "toggle").trim().toLowerCase(Locale.US);
                    JSONArray steps = updated.optJSONArray("steps");
                    if (name.isEmpty() || steps == null || steps.length() == 0)
                        throw new IllegalArgumentException("Cheat needs a name and at least one step.");
                    if (!mode.equals("toggle") && !mode.equals("once"))
                        throw new IllegalArgumentException("Cheat mode must be 'toggle' or 'once'.");
                    updated.put("mode", mode);
                    for (int n = 0; n < steps.length(); n++) {
                        JSONObject step = steps.optJSONObject(n);
                        if (step == null || step.optString("op", "").trim().isEmpty())
                            throw new IllegalArgumentException("Step " + (n + 1) + " must be a JSON object with an op.");
                        if ("patch".equalsIgnoreCase(step.optString("op"))) {
                            if (step.optString("addr", "").trim().isEmpty())
                                throw new IllegalArgumentException("Patch step " + (n + 1) + " needs an address.");
                            if (encodeCheatValue(step.optString("type", "hex"), step.optString("value", ""), step.optBoolean("nul", false)).length == 0)
                                throw new IllegalArgumentException("Patch step " + (n + 1) + " needs a non-empty valid value for its type.");
                        }
                    }
                    gameFile.getJSONArray("cheats").put(index, updated);
                    refreshCheatList();
                    log("Updated complete cheat: " + name);
                } catch (Exception e) { toast("Cheat not saved: " + e.getMessage()); }
            }).setNeutralButton("EDIT STEPS", (dialog, which) -> editCheatSteps(index, cheat)).show();
    }

    private void editCheatSteps(int cheatIndex, JSONObject cheat) {
        if (!enabledCheats.isEmpty()) { toast("Restore active cheats before editing."); return; }
        JSONArray steps = cheat.optJSONArray("steps");
        if (steps == null) steps = new JSONArray();
        final JSONArray working;
        try { working = new JSONArray(steps.toString()); }
        catch (JSONException e) { toast("Could not copy cheat steps: " + e.getMessage()); return; }
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(8), dp(12), dp(8));
        TextView help = text("Select a step to edit. Add and remove steps here; unknown step fields are preserved when editing.", 12, MUTED, false);
        panel.addView(help);
        LinearLayout actions = new LinearLayout(this);
        Button add = button("ADD STEP", PANEL);
        Button addRepeat = button("ADD REPEAT", PANEL);
        Button edit = button("EDIT SELECTED", PANEL);
        Button remove = button("REMOVE", PANEL);
        actions.addView(add, new LinearLayout.LayoutParams(0, dp(44), 1));
        actions.addView(addRepeat, new LinearLayout.LayoutParams(0, dp(44), 1));
        actions.addView(edit, new LinearLayout.LayoutParams(0, dp(44), 1));
        actions.addView(remove, new LinearLayout.LayoutParams(0, dp(44), 1));
        panel.addView(actions);
        LinearLayout orderActions = new LinearLayout(this);
        Button moveUp = button("MOVE STEP UP", PANEL);
        Button moveDown = button("MOVE STEP DOWN", PANEL);
        orderActions.addView(moveUp, new LinearLayout.LayoutParams(0, dp(42), 1));
        orderActions.addView(moveDown, new LinearLayout.LayoutParams(0, dp(42), 1));
        panel.addView(orderActions);
        final int[] selected = {-1};
        final TextView selection = text("No step selected", 12, TEXT, false);
        panel.addView(selection);
        Runnable updateSelection = () -> {
            if (selected[0] >= 0 && selected[0] < working.length()) {
                JSONObject step = working.optJSONObject(selected[0]);
                selection.setText("Selected step " + (selected[0] + 1) + ": " + (step == null ? "Invalid step" : step.optString("op", "unknown") + " / " + step.optString("type", "n/a")));
            } else { selected[0] = -1; selection.setText("No step selected"); }
        };
        Runnable showPicker = () -> {
            if (working.length() == 0) { toast("No steps. Use ADD STEP."); return; }
            String[] labels = new String[working.length()];
            for (int i = 0; i < working.length(); i++) {
                JSONObject step = working.optJSONObject(i);
                labels[i] = (i + 1) + ". " + (step == null ? "Invalid step" : step.optString("op", "?") + " · " + step.optString("addr", "no address"));
            }
            new android.app.AlertDialog.Builder(this).setTitle("Select cheat step").setItems(labels, (d, which) -> { selected[0] = which; updateSelection.run(); }).show();
        };
        edit.setOnClickListener(v -> {
            if (selected[0] < 0 || selected[0] >= working.length()) { showPicker.run(); return; }
            JSONObject current = working.optJSONObject(selected[0]);
            if (current == null) { toast("Selected step is not a valid object."); return; }
            if ("repeat".equalsIgnoreCase(current.optString("op"))) showRepeatGroupEditor(working, selected[0], current, false, updateSelection);
            else showCheatStepEditor(working, selected[0], current, false, updateSelection);
        });
        add.setOnClickListener(v -> {
            JSONObject fresh = new JSONObject();
            try { fresh.put("op", "patch"); fresh.put("type", "hex"); fresh.put("addr", "0x"); fresh.put("value", "00"); }
            catch (JSONException e) { toast(e.getMessage()); return; }
            showCheatStepEditor(working, working.length(), fresh, true, updateSelection);
        });
        addRepeat.setOnClickListener(v -> {
            try {
                JSONObject repeat=new JSONObject();repeat.put("op","repeat");repeat.put("count",2);repeat.put("delay_ms",0);
                JSONArray nested=new JSONArray();JSONObject patch=new JSONObject();patch.put("op","patch");patch.put("type","hex");patch.put("addr","0x");patch.put("value","00");nested.put(patch);repeat.put("steps",nested);
                showRepeatGroupEditor(working,working.length(),repeat,true,updateSelection);
            } catch(JSONException ex){toast("Could not create repeat group: "+ex.getMessage());}
        });
        remove.setOnClickListener(v -> {
            if (selected[0] < 0 || selected[0] >= working.length()) { showPicker.run(); return; }
            working.remove(selected[0]); selected[0] = -1; updateSelection.run();
        });
        moveUp.setOnClickListener(v -> {
            if (selected[0] < 0 || selected[0] >= working.length()) { showPicker.run(); return; }
            if (selected[0] == 0) { toast("This is already the first step."); return; }
            try {
                Object previous = working.get(selected[0] - 1);
                Object current = working.get(selected[0]);
                working.put(selected[0] - 1, current);
                working.put(selected[0], previous);
                selected[0]--; updateSelection.run();
            } catch (JSONException e) { toast("Could not reorder step: " + e.getMessage()); }
        });
        moveDown.setOnClickListener(v -> {
            if (selected[0] < 0 || selected[0] >= working.length()) { showPicker.run(); return; }
            if (selected[0] >= working.length() - 1) { toast("This is already the last step."); return; }
            try {
                Object next = working.get(selected[0] + 1);
                Object current = working.get(selected[0]);
                working.put(selected[0] + 1, current);
                working.put(selected[0], next);
                selected[0]++; updateSelection.run();
            } catch (JSONException e) { toast("Could not reorder step: " + e.getMessage()); }
        });
        new android.app.AlertDialog.Builder(this).setTitle("Cheat steps (" + working.length() + ")")
            .setView(panel).setNegativeButton("CANCEL", null).setPositiveButton("SAVE STEPS", (d, w) -> {
                if (working.length() == 0) { toast("A cheat must contain at least one step."); return; }
                try {
                    for (int i = 0; i < working.length(); i++) {
                        JSONObject step = working.optJSONObject(i);
                        if (step == null || step.optString("op", "").trim().isEmpty()) throw new IllegalArgumentException("Step " + (i + 1) + " needs an operation.");
                        if ("patch".equalsIgnoreCase(step.optString("op"))) {
                            if (step.optString("addr", "").trim().isEmpty()) throw new IllegalArgumentException("Patch step " + (i + 1) + " needs an address.");
                            if (encodeCheatValue(step.optString("type","hex"),step.optString("value",""),step.optBoolean("nul",false)).length==0) throw new IllegalArgumentException("Patch step "+(i+1)+" needs a valid non-empty value for its type.");
                        }
                    }
                    cheat.put("steps", working);
                    gameFile.getJSONArray("cheats").put(cheatIndex, cheat);
                    refreshCheatList();
                    log("Saved " + working.length() + " structured cheat step(s) for " + cheat.optString("name") + ".");
                } catch (Exception e) { toast("Steps not saved: " + e.getMessage()); }
            }).show();
    }

    private void showRepeatGroupEditor(JSONArray target,int stepIndex,JSONObject original,boolean isNew,Runnable onChanged) {
        JSONObject draft;
        try{draft=new JSONObject(original.toString());}catch(JSONException e){toast(e.getMessage());return;}
        LinearLayout fields=new LinearLayout(this);fields.setOrientation(LinearLayout.VERTICAL);fields.setPadding(dp(16),dp(8),dp(16),dp(4));
        EditText count=new EditText(this);count.setSingleLine(true);count.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);count.setHint("Repeat count (1–1000)");count.setText(String.valueOf(draft.optInt("count",1)));fields.addView(count);
        EditText delay=new EditText(this);delay.setSingleLine(true);delay.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);delay.setHint("Delay between repeats in ms (0–60000)");delay.setText(String.valueOf(draft.optInt("delay_ms",0)));fields.addView(delay);
        EditText nested=new EditText(this);nested.setSingleLine(false);nested.setMinLines(5);nested.setGravity(android.view.Gravity.TOP);nested.setTypeface(android.graphics.Typeface.MONOSPACE);nested.setHint("Nested steps JSON");JSONArray initial=draft.optJSONArray("steps");String nestedJson="[]";if(initial!=null){try{nestedJson=initial.toString(2);}catch(JSONException ignored){nestedJson=initial.toString();}}nested.setText(nestedJson);fields.addView(nested);
        new android.app.AlertDialog.Builder(this).setTitle(isNew?"Add repeat group":"Edit repeat group").setMessage("Nested steps must be patch operations or repeat groups. Repeat count is limited to 1,000 and nesting depth to 8.")
            .setView(fields).setNegativeButton("CANCEL",null).setPositiveButton("SAVE",(d,w)->{
                try{
                    int repetitions=Integer.parseInt(count.getText().toString().trim()),wait=Integer.parseInt(delay.getText().toString().trim());
                    if(repetitions<1||repetitions>1000)throw new IllegalArgumentException("Repeat count must be 1–1000.");
                    if(wait<0||wait>60000)throw new IllegalArgumentException("Delay must be 0–60000 ms.");
                    JSONArray children=new JSONArray(nested.getText().toString());
                    if(children.length()==0)throw new IllegalArgumentException("Add at least one nested step.");
                    validatePatchTree(children,0);
                    draft.put("op","repeat");draft.put("count",repetitions);draft.put("delay_ms",wait);draft.put("steps",children);
                    if(isNew)target.put(draft);else target.put(stepIndex,draft);
                    onChanged.run();
                }catch(Exception ex){toast("Repeat group not saved: "+ex.getMessage());}
            }).show();
    }

    private void validatePatchTree(JSONArray steps,int depth)throws Exception{
        if(depth>8)throw new IllegalArgumentException("Repeat nesting cannot exceed 8 levels.");
        for(int i=0;i<steps.length();i++){
            JSONObject step=steps.optJSONObject(i);
            if(step==null)throw new IllegalArgumentException("Nested step "+(i+1)+" must be an object.");
            String op=step.optString("op","").toLowerCase(Locale.US);
            if("repeat".equals(op)){
                int count=step.optInt("count",1),delay=step.optInt("delay_ms",0);JSONArray children=step.optJSONArray("steps");
                if(count<1||count>1000||delay<0||delay>60000||children==null||children.length()==0)
                    throw new IllegalArgumentException("Nested repeat "+(i+1)+" has invalid count, delay or children.");
                validatePatchTree(children,depth+1);
            }else if("patch".equals(op)||"write".equals(op)||"check".equals(op)){
                if(step.optString("addr","").trim().isEmpty())throw new IllegalArgumentException("Nested "+op+" "+(i+1)+" needs an address.");
                if(encodeCheatValue(step.optString("type","hex"),step.optString("value",""),step.optBoolean("nul",false)).length==0)
                    throw new IllegalArgumentException("Nested "+op+" "+(i+1)+" has an invalid value.");
            }else if("nop".equals(op)){
                int length=step.optInt("length",step.optInt("size",0));
                if(length<1||length>4096)throw new IllegalArgumentException("Nested NOP length must be 1–4096.");
            }else if("code".equals(op)){
                if(step.optString("addr","").trim().isEmpty()||step.optString("code",step.optString("value","")).trim().isEmpty())
                    throw new IllegalArgumentException("Nested code step needs address and assembly source.");
            }else if("read".equals(op)){
                if(step.optString("addr","").trim().isEmpty())throw new IllegalArgumentException("Nested read step needs an address.");
            }else if("call".equals(op)){
                if(step.optString("rip","").trim().isEmpty())throw new IllegalArgumentException("Nested call step needs rip.");
                JSONArray args=step.optJSONArray("args");if(args!=null&&args.length()>6)throw new IllegalArgumentException("Call supports at most six arguments.");
            }else if(!"log".equals(op)&&!"sleep".equals(op)){
                throw new IllegalArgumentException("Unsupported nested operation: "+op);
            }
        }
    }

    private void showCheatStepEditor(JSONArray target, int stepIndex, JSONObject original, boolean isNew, Runnable onChanged) {
        JSONObject draft;
        try { draft = new JSONObject(original.toString()); }
        catch (JSONException e) { toast(e.getMessage()); return; }
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(16), dp(8), dp(16), dp(4));
        EditText op = new EditText(this); op.setSingleLine(true); op.setHint("Operation (e.g. patch)"); op.setText(draft.optString("op", "")); fields.addView(op);
        EditText type = new EditText(this); type.setSingleLine(true); type.setHint("Value type (e.g. hex)"); type.setText(draft.optString("type", "hex")); fields.addView(type);
        EditText address = new EditText(this); address.setSingleLine(true); address.setHint("Address / expression"); address.setText(draft.optString("addr", "")); fields.addView(address);
        EditText value = new EditText(this); value.setSingleLine(true); value.setHint("Value (hex for patch)"); value.setText(draft.optString("value", "")); fields.addView(value);
        new android.app.AlertDialog.Builder(this).setTitle(isNew ? "Add cheat step" : "Edit cheat step")
            .setMessage("For patch steps, enter an address and a value matching its type (hex, integer, float, or UTF text). Other operation types are retained for library editing but are not executable by this mobile build.")
            .setView(fields).setNegativeButton("CANCEL", null).setPositiveButton("SAVE", (d, w) -> {
                try {
                    String operation = op.getText().toString().trim();
                    if (operation.isEmpty()) throw new IllegalArgumentException("Operation is required.");
                    draft.put("op", operation);
                    if (!type.getText().toString().trim().isEmpty()) draft.put("type", type.getText().toString().trim());
                    if (!address.getText().toString().trim().isEmpty()) draft.put("addr", address.getText().toString().trim());
                    if (!value.getText().toString().trim().isEmpty()) draft.put("value", value.getText().toString().trim());
                    if ("patch".equalsIgnoreCase(operation)) {
                        if (draft.optString("addr", "").trim().isEmpty()) throw new IllegalArgumentException("Patch address is required.");
                        if (encodeCheatValue(draft.optString("type", "hex"), draft.optString("value", ""), draft.optBoolean("nul", false)).length == 0) throw new IllegalArgumentException("Patch value must be valid and non-empty for its type.");
                    }
                    target.put(stepIndex, draft);
                    onChanged.run();
                } catch (Exception e) { toast("Step not saved: " + e.getMessage()); }
            }).show();
    }

    private void moveCheat(int index, int delta) {
        if (gameFile == null || !enabledCheats.isEmpty()) { toast("Restore active cheats before reordering."); return; }
        JSONArray cheats = gameFile.optJSONArray("cheats");
        int target = index + delta;
        if (cheats == null || target < 0 || target >= cheats.length()) return;
        try {
            JSONObject moving = cheats.getJSONObject(index);
            JSONObject other = cheats.getJSONObject(target);
            cheats.put(index, other);
            cheats.put(target, moving);
            refreshCheatList();
        } catch (JSONException e) { toast(e.getMessage()); }
    }

    private void deleteCheat(int index) {
        if (gameFile == null || !enabledCheats.isEmpty()) { toast("Restore active cheats before deleting."); return; }
        JSONArray old = gameFile.optJSONArray("cheats");
        if (old == null || index < 0 || index >= old.length()) return;
        JSONArray next = new JSONArray();
        for (int i = 0; i < old.length(); i++) if (i != index) next.put(old.opt(i));
        try {
            gameFile.put("cheats", next);
            refreshCheatList();
            log("Deleted cheat at position " + (index + 1) + ".");
        } catch (JSONException e) { toast(e.getMessage()); }
    }

    private void turnAllCheatsOff(){
        if(!connected){toast("Connect to the PS5 first.");return;}
        if(enabledCheats.isEmpty()){toast("No active toggle cheats to restore.");return;}
        runAsync("Restoring all active cheats",()->{
            int p=currentPid();List<Integer> indexes=new ArrayList<>(enabledCheats.keySet());
            Collections.sort(indexes,Collections.reverseOrder());
            JSONArray cheats=gameFile==null?null:gameFile.optJSONArray("cheats");
            int restoredCount=0;
            for(int index:indexes){
                List<UndoPatch> undo=enabledCheats.get(index);
                if(undo==null)continue;
                List<UndoPatch> reverse=new ArrayList<>(undo);Collections.reverse(reverse);
                for(UndoPatch patch:reverse){
                    client.write(p,patch.address,patch.original);
                    byte[] check=client.read(p,patch.address,patch.original.length);
                    if(!Arrays.equals(check,patch.original))
                        throw new IOException("Turn-off-all stopped: restore verification failed at 0x"+Long.toHexString(patch.address).toUpperCase(Locale.US)+". Remaining active cheats are retained.");
                }
                JSONObject cheat=cheats!=null&&index>=0&&index<cheats.length()?cheats.optJSONObject(index):null;
                if(cheat!=null)runOffPatchSteps(p,cheat);
                enabledCheats.remove(index);restoredCount++;
            }
            return "Restored "+restoredCount+" active cheat(s); all completed writes were verified.";
        },result->{log(result);refreshCheatList();});
    }

    private void toggleCheat(int index, JSONObject cheat) {
        if (!connected) { toast("Connect to the PS5 first."); return; }
        final boolean isOn = enabledCheats.containsKey(index);
        runAsync(isOn ? "Restoring cheat" : "Applying cheat", () -> {
            int p = currentPid();
            ProcInfo info = client.procInfo(p);
            String expected = gameFile == null ? "" : gameFile.optString("title_id", "");
            if (!expected.isEmpty() && !info.titleId.isEmpty() && !expected.equalsIgnoreCase(info.titleId)) {
                throw new IllegalStateException("Title ID mismatch. Cheat file is for " + expected + ", selected process is " + info.titleId + ". Select the matching game/version.");
            }
            if (isOn) {
                List<UndoPatch> undo = enabledCheats.get(index);
                if (undo == null) throw new IllegalStateException("Cheat restore state is missing; no bytes were changed.");
                List<UndoPatch> reverse = new ArrayList<>(undo);
                Collections.reverse(reverse);
                for (UndoPatch u : reverse) {
                    client.write(p, u.address, u.original);
                    byte[] restored = client.read(p, u.address, u.original.length);
                    if (!Arrays.equals(u.original, restored))
                        throw new IOException("Restore verification failed at 0x" + Long.toHexString(u.address).toUpperCase(Locale.US) + ".");
                }
                runOffPatchSteps(p, cheat);
                enabledCheats.remove(index);
                return "Restored " + cheat.optString("name") + ".";
            }
            List<UndoPatch> undo = new ArrayList<>();
            List<String> stepOutput=new ArrayList<>();
            JSONArray steps = cheat.optJSONArray("steps");
            if (steps == null) throw new IllegalArgumentException("Cheat has no steps.");
            try {
                applyPatchSteps(p, steps, undo, stepOutput, 0);
            } catch (Exception applyFailure) {
                Collections.reverse(undo);
                for (UndoPatch u : undo) {
                    try {
                        client.write(p, u.address, u.original);
                        byte[] restored = client.read(p, u.address, u.original.length);
                        if (!Arrays.equals(u.original, restored))
                            throw new IOException("Rollback verification failed at 0x" + Long.toHexString(u.address).toUpperCase(Locale.US) + ".");
                    } catch (Exception rollbackFailure) { applyFailure.addSuppressed(rollbackFailure); }
                }
                throw applyFailure;
            }
            boolean runOnce = "once".equalsIgnoreCase(cheat.optString("mode", "toggle"));
            if (!runOnce) enabledCheats.put(index, undo);
            StringBuilder report=new StringBuilder((runOnce ? "Ran " : "Applied ") + cheat.optString("name") + (runOnce ? " once." : "."));
            for(String line:stepOutput)report.append("\\n").append(line);
            return report.toString();
        }, result -> {
            log(result);
            refreshCheatList();
        });
    }

    private void applyPatchSteps(int processId, JSONArray steps, List<UndoPatch> undo, List<String> stepOutput, int depth) throws Exception {
        if(depth>8)throw new IllegalArgumentException("Repeat steps are nested too deeply (maximum 8 levels).");
        for(int i=0;i<steps.length();i++){
            JSONObject step=steps.optJSONObject(i);
            if(step==null)throw new IllegalArgumentException("Step "+(i+1)+" is not a JSON object.");
            String op=step.optString("op","").toLowerCase(Locale.US);
            if("log".equals(op)){
                stepOutput.add("LOG: "+step.optString("text",step.optString("message","")));
                continue;
            }
            if("sleep".equals(op)){
                int delay=step.optInt("ms",100);
                if(delay<0||delay>60000)throw new IllegalArgumentException("Sleep step must be 0–60000 ms.");
                if(delay>0)Thread.sleep(delay);
                stepOutput.add("WAIT: "+delay+" ms");
                continue;
            }
            if("call".equals(op)){
                long target=resolveAddress(step.getString("rip"),processId);boolean executable=false;
                for(MapEntry map:client.maps(processId))if((map.prot&4)!=0&&Long.compareUnsigned(target,map.start)>=0&&Long.compareUnsigned(target,map.end)<0){executable=true;break;}
                if(!executable)throw new IOException("Call target is not inside an executable memory map.");
                JSONArray args=step.optJSONArray("args");if(args!=null&&args.length()>6)throw new IllegalArgumentException("Call step supports at most six integer arguments.");
                long[] values=new long[6];
                if(args!=null)for(int n=0;n<args.length();n++){
                    Object raw=args.get(n);String value=String.valueOf(raw).trim();
                    values[n]=value.matches("(?i)0x[0-9a-f]+")?Long.parseUnsignedLong(value.substring(2),16):Long.parseLong(value);
                }
                long result=client.callSafe(processId,target,values);
                stepOutput.add("CALL 0x"+Long.toHexString(target).toUpperCase(Locale.US)+" -> RAX 0x"+Long.toHexString(result).toUpperCase(Locale.US)+" (not retried)");
                continue;
            }
            if("repeat".equals(op)){
                int count=step.optInt("count",1),delay=step.optInt("delay_ms",0);
                JSONArray nested=step.optJSONArray("steps");
                if(count<1||count>1000)throw new IllegalArgumentException("Repeat count must be between 1 and 1000.");
                if(delay<0||delay>60000)throw new IllegalArgumentException("Repeat delay must be between 0 and 60000 ms.");
                if(nested==null||nested.length()==0)throw new IllegalArgumentException("Repeat step "+(i+1)+" needs nested steps.");
                for(int n=0;n<count;n++){
                    applyPatchSteps(processId,nested,undo,stepOutput,depth+1);
                    if(delay>0&&n<count-1)Thread.sleep(delay);
                }
                continue;
            }
            if(!"patch".equals(op)&&!"write".equals(op)&&!"check".equals(op)&&!"nop".equals(op)&&!"code".equals(op)&&!"read".equals(op)&&!"call".equals(op))
                throw new IllegalArgumentException("Supported steps: patch, write, check, read, call, nop, code, log, sleep and repeat. Unsupported step: "+op);
            long address=resolveAddress(step.getString("addr"),processId);
            if("read".equals(op)){
                String readType=step.optString("type",step.has("length")||step.has("size")?"hex":"u32").toLowerCase(Locale.US);
                int typeIndex=cheatReadTypeIndex(readType);
                int length;
                if(typeIndex<0){
                    length=step.optInt("length",step.optInt("size",0));
                    if(length<1||length>4096)throw new IllegalArgumentException("Hex read step length must be 1–4096 bytes.");
                }else length=typeIndex==1||typeIndex==2?1:typeIndex==3||typeIndex==4?2:typeIndex==5||typeIndex==6||typeIndex==9?4:8;
                byte[] observed=client.read(processId,address,length);
                String decoded=typeIndex<0?toHex(observed):decodeAddressValue(typeIndex,observed);
                stepOutput.add("READ 0x"+Long.toHexString(address).toUpperCase(Locale.US)+" "+readType+" = "+decoded+" ["+toHex(observed)+"]");
                continue;
            }
            byte[] patch;
            if("nop".equals(op)){
                int length=step.optInt("length",step.optInt("size",0));
                if(length<1||length>4096)throw new IllegalArgumentException("NOP step length must be 1–4096 bytes.");
                patch=new byte[length];Arrays.fill(patch,(byte)0x90);
            }else if("code".equals(op)){
                String source=step.optString("code",step.optString("value",""));
                if(source.trim().isEmpty())throw new IllegalArgumentException("Code step needs assembly source in 'code' or 'value'.");
                patch=assembleMini(source,address);
                if(patch.length==0||patch.length>4096)throw new IllegalArgumentException("Assembled code must be 1–4096 bytes.");
            }else{
                patch=encodeCheatValue(step.optString("type","hex"),step.optString("value",""),step.optBoolean("nul",false));
            }
            if(patch.length==0)throw new IllegalArgumentException("Empty "+op+" step.");
            byte[] original=client.read(processId,address,patch.length);
            if("check".equals(op)){
                if(!Arrays.equals(patch,original))throw new IOException("Check step "+(i+1)+" failed at 0x"+Long.toHexString(address).toUpperCase(Locale.US)+": memory does not match expected bytes.");
                continue;
            }
            undo.add(new UndoPatch(address,original));
            client.write(processId,address,patch);
            byte[] written=client.read(processId,address,patch.length);
            if(!Arrays.equals(patch,written))throw new IOException("Patch read-back verification failed at 0x"+Long.toHexString(address).toUpperCase(Locale.US)+".");
        }
    }

    private int cheatReadTypeIndex(String type){
        switch(type){
            case "u8":case "byte":return 1;
            case "i8":return 2;
            case "u16":return 3;
            case "i16":return 4;
            case "u32":return 5;
            case "i32":return 6;
            case "u64":case "ptr":return 7;
            case "i64":return 8;
            case "f32":case "float":return 9;
            case "f64":case "double":return 10;
            case "hex":case "bytes":return -1;
            default:throw new IllegalArgumentException("Unsupported read type: "+type);
        }
    }

    private void runOffPatchSteps(int processId, JSONObject cheat) throws Exception {
        JSONArray off=cheat.optJSONArray("off");
        if(off==null||off.length()==0)return;
        for(int i=0;i<off.length();i++){
            JSONObject step=off.optJSONObject(i);
            if(step==null)throw new IllegalArgumentException("Off step "+(i+1)+" is not an object.");
            String op=step.optString("op","").toLowerCase(Locale.US);
            if(!"patch".equals(op)&&!"write".equals(op)&&!"check".equals(op)&&!"nop".equals(op)&&!"code".equals(op))
                throw new IllegalArgumentException("Off list supports patch/write/check/nop/code steps only; unsupported step: "+op);
            long address=resolveAddress(step.getString("addr"),processId);
            byte[] value;
            if("nop".equals(op)){
                int length=step.optInt("length",step.optInt("size",0));
                if(length<1||length>4096)throw new IllegalArgumentException("Off NOP step length must be 1–4096 bytes.");
                value=new byte[length];Arrays.fill(value,(byte)0x90);
            }else if("code".equals(op)){
                String source=step.optString("code",step.optString("value",""));
                if(source.trim().isEmpty())throw new IllegalArgumentException("Off code step needs assembly source.");
                value=assembleMini(source,address);
                if(value.length==0||value.length>4096)throw new IllegalArgumentException("Off assembled code must be 1–4096 bytes.");
            }else{
                value=encodeCheatValue(step.optString("type","hex"),step.optString("value",""),step.optBoolean("nul",false));
            }
            if(value.length==0)throw new IllegalArgumentException("Off "+op+" step "+(i+1)+" has an empty value.");
            byte[] before=client.read(processId,address,value.length);
            if("check".equals(op)){
                if(!Arrays.equals(value,before))throw new IOException("Off check step "+(i+1)+" failed at 0x"+Long.toHexString(address).toUpperCase(Locale.US)+".");
                continue;
            }
            try{
                client.write(processId,address,value);
                byte[] verify=client.read(processId,address,value.length);
                if(!Arrays.equals(value,verify))throw new IOException("Off patch verification failed at 0x"+Long.toHexString(address).toUpperCase(Locale.US)+".");
            }catch(Exception failure){
                try{client.write(processId,address,before);byte[] back=client.read(processId,address,before.length);if(!Arrays.equals(before,back))throw new IOException("Off-step rollback verification failed.");}
                catch(Exception rollback){failure.addSuppressed(rollback);}
                throw failure;
            }
        }
    }

    private long resolveAddress(String expression, int processId) throws Exception {
        String s = expression.trim();
        if (s.matches("(?i)0x[0-9a-f]+")) return Long.parseUnsignedLong(s.substring(2), 16);
        if (s.matches("[0-9]+")) return Long.parseLong(s);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)^module\\(([^)]+)\\)\\s*\\+\\s*(0x[0-9a-f]+|[0-9]+)$").matcher(s);
        if (!m.find()) throw new IllegalArgumentException("Unsupported address expression: " + s);
        String module = m.group(1).trim();
        long offset = m.group(2).toLowerCase(Locale.US).startsWith("0x")
                ? Long.parseUnsignedLong(m.group(2).substring(2), 16) : Long.parseLong(m.group(2));
        List<MapEntry> all = client.maps(processId);
        MapEntry chosen = null;
        if (module.equalsIgnoreCase("main")) {
            for (MapEntry entry : all) if ((entry.prot & 4) != 0 && (chosen == null || entry.start < chosen.start)) chosen = entry;
        } else {
            for (MapEntry entry : all) {
                if (entry.name.toLowerCase(Locale.US).contains(module.toLowerCase(Locale.US))) {
                    if (chosen == null || entry.start < chosen.start) chosen = entry;
                }
            }
        }
        if (chosen == null) throw new IllegalArgumentException("Module not found: " + module);
        return chosen.start + offset;
    }

    private byte[] encodeCheatValue(String type, String value, boolean nul) {
        String t=type==null?"hex":type.trim().toLowerCase(Locale.US);
        switch(t) {
            case "hex": case "bytes": return parseHex(value);
            case "u8": case "i8": return new byte[]{(byte)Long.decode(value.trim()).longValue()};
            case "u16": case "i16": return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short)Long.decode(value.trim()).longValue()).array();
            case "u32": case "i32": return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt((int)Long.decode(value.trim()).longValue()).array();
            case "u64": case "i64": case "ptr": {
                String v=value.trim();long n;
                try { n=Long.decode(v); }
                catch(NumberFormatException ex) {
                    if(v.matches("(?i)0x[0-9a-f]+")) n=Long.parseUnsignedLong(v.substring(2),16);
                    else throw ex;
                }
                return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(n).array();
            }
            case "f32": return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(Float.parseFloat(value.trim())).array();
            case "f64": return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(Double.parseDouble(value.trim())).array();
            case "utf8": case "utf-8": case "string": {
                byte[] data=value.getBytes(StandardCharsets.UTF_8);
                if(!nul)return data;
                return Arrays.copyOf(data,data.length+1);
            }
            case "utf16": case "utf16le": case "utf-16le": {
                byte[] data=value.getBytes(java.nio.charset.Charset.forName("UTF-16LE"));
                if(!nul)return data;
                return Arrays.copyOf(data,data.length+2);
            }
            default: throw new IllegalArgumentException("Unsupported patch value type: "+type);
        }
    }

    private static byte[] parseHex(String input) {
        String s = input.replace("0x", "").replace("0X", "").replaceAll("[^0-9A-Fa-f]", "");
        if (s.length() % 2 != 0) throw new IllegalArgumentException("Hex data must contain complete byte pairs.");
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte)Integer.parseInt(s.substring(i*2, i*2+2), 16);
        return out;
    }

    private static String toHex(byte[] data) {
        StringBuilder b = new StringBuilder();
        for (byte v : data) { if (b.length() > 0) b.append(' '); b.append(String.format(Locale.US, "%02X", v & 255)); }
        return b.toString();
    }

    private static String hexDump(byte[] data, long base) {
        StringBuilder b = new StringBuilder();
        for (int off = 0; off < data.length; off += 16) {
            b.append(String.format(Locale.US, "%08X  ", base + off));
            int n = Math.min(16, data.length - off);
            for (int j = 0; j < 16; j++) b.append(j < n ? String.format(Locale.US, "%02X ", data[off+j] & 255) : "   ");
            b.append(" ");
            for (int j = 0; j < n; j++) { int c = data[off+j] & 255; b.append(c >= 32 && c <= 126 ? (char)c : '.'); }
            b.append("\n");
        }
        return b.toString();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onDestroy() {
        stopDebugEventListener();
        stopKernelLog();
        super.onDestroy();
        worker.shutdownNow();
        cancelWorker.shutdownNow();
        debugEventWorker.shutdownNow();
        kernelLogWorker.shutdownNow();
        if (client != null) client.close();
        if (scanClient != null) scanClient.close();
    }

    private static final class TurboSegment { final long start; final int length; TurboSegment(long s,int n){start=s;length=n;} }
    private static final class DisasmEntry {
        final long address; final int length, kind; final byte[] bytes;
        DisasmEntry(long a, int l, int k, byte[] b) { address=a; length=l; kind=k; bytes=b; }
    }
    private static final class UndoPatch {
        final long address; final byte[] original;
        UndoPatch(long a, byte[] o) { address = a; original = o; }
    }

    private static final class ProcessEntry {
        final String name; final int pid;
        ProcessEntry(String n, int p) { name = n; pid = p; }
    }
    private static final class ProcInfo {
        int pid; String name, path, titleId, contentId;
    }
    private static final class MapEntry {
        String name; long start, end, offset; int prot;
    }

    private static final class PS5Client {
        private static final int MAGIC = (int)0xFFAABBCC;
        private static final int SUCCESS = (int)0x80000000;
        // Hard ceilings prevent malformed console responses or accidental callers from
        // allocating unbounded buffers while the protocol stream is being decoded.
        private static final int MAX_REQUEST_BYTES = 1024 * 1024;
        private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;
        private static final int CMD_VERSION = (int)0xBD000001;
        private static final int CMD_FW_VERSION = (int)0xBD000500, CMD_BRANDING = (int)0xBD000501, CMD_PLATFORM_ID = (int)0xBD000502;
        private static final int CMD_TURBO_CAPS = (int)0xBDAACC10, CMD_TURBO_START = (int)0xBDAACC11, CMD_TURBO_COUNT = (int)0xBDAACC12, CMD_TURBO_GET = (int)0xBDAACC13, CMD_TURBO_END = (int)0xBDAACC14, CMD_TURBO_CANCEL = (int)0xBDAACC17, CMD_PROC_AUTH = (int)0xBDAACCFF;
        private boolean authenticated=false;
        private int activeTurboPid=0, activeTurboType=4, turboEngines=0;
        private static final int CMD_PROC_LIST = (int)0xBDAA0001;
        private static final int CMD_PROC_ALLOC = (int)0xBDAA000B;
        private static final int CMD_PROC_FREE = (int)0xBDAA000C;
        private static final int CMD_PROC_CALL = (int)0xBDAA0006;
        private static final int CMD_PROC_READ_MULTI = (int)0xBDAACC03;
        private static final int CMD_PROC_WRITE_MULTI = (int)0xBDAACC04;
        private static final int CMD_PROC_READ = (int)0xBDAA0002;
        private static final int CMD_PROC_WRITE = (int)0xBDAA0003;
        private static final int CMD_PROC_MAPS = (int)0xBDAA0004;
        private static final int CMD_TURBO_REGIONS = (int)0xBDAACC16;
        private static final int CMD_PROC_INFO = (int)0xBDAA000A;
        private static final int CMD_PROC_DISASM_REGION = (int)0xBDAA0020;
        private static final int CMD_PROC_FIND_XREFS_TO = (int)0xBDAA0022;
        private static final int CMD_PROC_READ_STACK = (int)0xBDAA0023;
        private static final int CMD_DEBUG_ATTACH = (int)0xBDBB0001;
        private static final int CMD_DEBUG_DETACH = (int)0xBDBB0002;
        private static final int CMD_DEBUG_SET_BREAKPOINT = (int)0xBDBB0003;
        private static final int CMD_DEBUG_SET_WATCHPOINT = (int)0xBDBB0004;
        private static final int CMD_DEBUG_GET_THREAD_LIST = (int)0xBDBB0005;
        private static final int CMD_DEBUG_GETREGS = (int)0xBDBB0008;
        private static final int CMD_DEBUG_STEP = (int)0xBDBB0012;
        private static final int CMD_DEBUG_STEP_THREAD = (int)0xBDBB0013;
        private static final int CMD_DEBUG_CONTINUE = (int)0xBDBB0010;
        private static final int TRAMP_SLOT=64,TRAMP_POOL=0x4000;
        private static final class TrampolinePool {
            long base;int used;final Map<Long,Long> slots=new HashMap<>();
            TrampolinePool(long base){this.base=base;}
        }
        private final Map<Integer,TrampolinePool> trampolinePools=new HashMap<>();
        private long lastRemoteCallMillis=0,lastHijackMillis=0;
        private Socket socket;
        private DataInputStream in;
        private OutputStream out;

        synchronized String consoleInfo() throws IOException {
            ensureConnected();
            send(CMD_FW_VERSION,new byte[0]);
            int firmware=readUShortLE();
            send(CMD_BRANDING,new byte[0]);
            int length=readIntLE();
            if(length<1||length>4096)throw new IOException("Invalid payload branding length: "+length);
            byte[] brandBytes=readBytes(length);
            int split=0;while(split<brandBytes.length&&brandBytes[split]!=0)split++;
            String branding=new String(brandBytes,0,split,StandardCharsets.UTF_8);
            String capability=split<brandBytes.length?new String(brandBytes,split+1,brandBytes.length-split-1,StandardCharsets.UTF_8):"unknown";
            send(CMD_PLATFORM_ID,new byte[0]);
            int platform=readUShortLE();
            return "Firmware: "+firmware+"\nProtocol: ps5debug-NG 1.3\nPlatform ID: "+platform
                +"\nBrand: "+branding+"\nCapability level: "+capability;
        }

        synchronized String connect(String host, int port) throws Exception {
            close();
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), 8000);
                socket.setSoTimeout(15000);
                socket.setTcpNoDelay(true);
                in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                out = new BufferedOutputStream(socket.getOutputStream());
                send(CMD_VERSION, new byte[0]);
                int n = readIntLE();
                if (n < 0 || n > 4096) throw new IOException("Unexpected version response length: " + n);
                byte[] b = readBytes(n);
                return new String(b, StandardCharsets.UTF_8);
            } catch (Exception e) {
                close();
                throw e;
            }
        }

        synchronized void close() {
            try { if (socket != null) socket.close(); } catch (Exception ignored) {}
            socket = null; in = null; out = null; authenticated=false;
        }
        synchronized boolean isConnected(){return socket!=null&&socket.isConnected()&&!socket.isClosed();}
        static final class TurboCaps { int version,engines,threads; }
        static final class TurboResult { long address; byte[] current,previous; }
        static final class TurboRegion { long start,end; int prot,flags,mbps; }
        synchronized TurboCaps turboCaps() throws IOException {
            if(!isConnected()) throw new IOException("Not connected.");
            socket.setSoTimeout(4000);
            try { send(CMD_TURBO_CAPS,new byte[0]); int st=readIntLE(); if(st!=SUCCESS)return null; byte[] raw=readBytes(16); ByteBuffer b=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN); TurboCaps c=new TurboCaps(); c.version=b.getInt();c.engines=b.getInt();c.threads=b.getInt();b.getInt();turboEngines=c.engines;return c; }
            catch(java.net.SocketTimeoutException e){close();return null;}
            finally{if(isConnected())socket.setSoTimeout(15000);}
        }
        private static byte[] authKey(){long mask=0xFFFFFFFFL,s1=200,s2=300,s3=400,s4=500;byte[] o=new byte[256];for(int i=0;i<256;i++){s1=((s1<<18)&0xFFF80000L)^(((s1^(s1<<6))&mask)>>>13);s2=((s2<<2)&0xFFFFFFE0L)^(((s2^(s2<<2))&mask)>>>27);s3=((s3<<7)&0xFFFFF800L)^(((s3^(s3<<13))&mask)>>>21);s4=((s4<<13)&0xFFF00000L)^(((s4^(s4<<3))&mask)>>>12);o[i]=(byte)(s1^s2^s3^s4);}return o;}
        synchronized void auth() throws IOException {if(authenticated)return;ByteBuffer h=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(0xBB40E64D).putInt(2);send(CMD_PROC_AUTH,h.array());expectSuccess("TurboScan auth");int n=readUShortLE();if(n>4096)throw new IOException("Invalid auth challenge.");byte[] ch=readBytes(n),key=authKey();for(int i=0;i<n;i++)ch[i]=(byte)(ch[i]^key[i&255]);out.write(ch);out.flush();expectSuccess("TurboScan auth response");authenticated=true;}
        private byte[] turboSegmentsBlob(List<TurboSegment> segments) throws IOException {
            if(segments==null||segments.isEmpty()||segments.size()>20000)throw new IOException("Invalid TurboScan region list.");
            ByteBuffer b=ByteBuffer.allocate(4+segments.size()*12).order(ByteOrder.LITTLE_ENDIAN);b.putInt(segments.size());
            for(TurboSegment x:segments){if(x.length<=0)throw new IOException("Invalid memory region length.");b.putLong(x.start).putInt(x.length);}return b.array();
        }
        synchronized List<TurboRegion> turboRegions(int pid) throws IOException {
            auth();send(CMD_TURBO_REGIONS,ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(pid).putInt(0).putInt(0).putInt(0).array());
            expectSuccess("TurboScan region classification");int n=readIntLE();if(n<0||n>20000)throw new IOException("Invalid TurboScan region count.");
            byte[] raw=readBytes(n*32);expectSuccess("TurboScan region classification completion");List<TurboRegion> out=new ArrayList<>();ByteBuffer b=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            for(int i=0;i<n;i++){TurboRegion r=new TurboRegion();r.start=b.getLong();r.end=b.getLong();r.prot=b.getInt();r.flags=b.getInt();r.mbps=b.getInt();b.getInt();out.add(r);}return out;
        }
        synchronized long turboFirstScan(int pid,List<TurboSegment> segments,int type,int compare,byte[] value,int alignment) throws IOException {
            auth();activeTurboPid=pid;activeTurboType=type;socket.setSoTimeout(120000);TurboSegment first=segments.get(0);byte[] blob=turboSegmentsBlob(segments);
            ByteBuffer h=ByteBuffer.allocate(27).order(ByteOrder.LITTLE_ENDIAN);h.putInt(pid).putLong(first.start).putInt(first.length).put((byte)type).put((byte)compare).put((byte)alignment).putInt(value.length).putInt(0x12);
            send(CMD_TURBO_START,h.array());expectSuccess("TurboScan start");out.write(value);out.flush();expectSuccess("TurboScan setup");out.write(blob);out.flush();
            byte[] raw=readBytes(12);ByteBuffer r=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);int stored=r.getInt();long count=r.getLong();
            if(stored==0){
                if(readLongLE()!=-1L)throw new IOException("Unexpected TurboScan decline response.");
                expectSuccess("TurboScan decline");
                if(type>9)throw new IOException("Too many matches for this byte-pattern scan. Narrow the range or use a longer pattern.");
                turboSnapshotFirstScan(pid,segments,type,alignment,!hasNonZero(value));
                long narrowed=turboNextScan(compare,value,value.length==0?valueLengthForType(type):value.length);
                socket.setSoTimeout(15000);
                return narrowed;
            }
            expectSuccess("TurboScan completion");socket.setSoTimeout(15000);return count;
        }
        private int valueLengthForType(int type) throws IOException {
            switch(type){case 0:case 1:return 1;case 2:case 3:return 2;case 4:case 5:case 8:return 4;case 6:case 7:case 9:return 8;default:throw new IOException("Unsupported TurboScan value type.");}
        }
        private boolean hasNonZero(byte[] value){if(value==null)return false;for(byte x:value)if(x!=0)return true;return false;}
        synchronized long turboUnknownFirstScan(int pid,List<TurboSegment> segments,int type,int alignment) throws IOException {
            if(type>9)throw new IOException("Unknown-value snapshots support numeric types only.");
            return turboSnapshotFirstScan(pid,segments,type,alignment,false);
        }
        private synchronized long turboSnapshotFirstScan(int pid,List<TurboSegment> segments,int type,int alignment,boolean includeZeros) throws IOException {
            auth();activeTurboPid=pid;activeTurboType=type;socket.setSoTimeout(120000);int flags=0x04|0x10|(includeZeros?0x08:0)|((turboEngines&0x40)!=0?0x20:0);TurboSegment first=segments.get(0);byte[] blob=turboSegmentsBlob(segments);
            ByteBuffer h=ByteBuffer.allocate(27).order(ByteOrder.LITTLE_ENDIAN);h.putInt(pid).putLong(first.start).putInt(first.length).put((byte)type).put((byte)11).put((byte)alignment).putInt(0).putInt(flags);
            send(CMD_TURBO_START,h.array());expectSuccess("TurboScan snapshot start");expectSuccess("TurboScan snapshot setup");out.write(blob);out.flush();
            long slots=readLongLE(),total=readLongLE();while(readLongLE()!=-1L){}int ok=readIntLE();long survivors=readLongLE();expectSuccess("TurboScan snapshot completion");socket.setSoTimeout(120000);
            if(ok==0)throw new IOException("Console could not store the memory snapshot; choose a smaller range or free snapshot storage.");
            return survivors;
        }
        synchronized long turboNextScan(int compare,byte[] value,int valueLength) throws IOException {
            auth();socket.setSoTimeout(120000);boolean needsValue=(compare==0||compare==1||compare==2||compare==3||compare==4||compare==6||compare==8);
            int dataLength=needsValue&&value.length>0?value.length:valueLength;
            ByteBuffer h=ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);h.putInt(activeTurboPid).putLong(0L).put((byte)activeTurboType).put((byte)compare).putInt(dataLength).putInt(0x02);
            send(CMD_TURBO_COUNT,h.array());expectSuccess("TurboScan next scan");if(needsValue&&value.length>0){out.write(value);out.flush();}
            while(readLongLE()!=-1L){} long count=readLongLE();expectSuccess("TurboScan next scan completion");socket.setSoTimeout(15000);return count;
        }
        synchronized List<TurboResult> turboGet(int start,int count,int valueLength) throws IOException {
            ByteBuffer h=ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putInt(start).putInt(count).putInt(0);send(CMD_TURBO_GET,h.array());expectSuccess("TurboScan get");
            int hdr=readIntLE(),n=hdr&0x7FFFFFFF;boolean three=(hdr&0x80000000)!=0;if(n<0||n>10000)throw new IOException("Invalid result count.");int rec=8+valueLength*(three?3:2);byte[] raw=readBytes(n*rec);expectSuccess("TurboScan get completion");
            List<TurboResult> rows=new ArrayList<>();for(int i=0;i<n;i++){int o=i*rec;TurboResult x=new TurboResult();x.address=ByteBuffer.wrap(raw,o,8).order(ByteOrder.LITTLE_ENDIAN).getLong();x.current=Arrays.copyOfRange(raw,o+8,o+8+valueLength);x.previous=Arrays.copyOfRange(raw,o+8+valueLength,o+8+2*valueLength);rows.add(x);}return rows;
        }
        synchronized void turboEnd() throws IOException {auth();send(CMD_TURBO_END,new byte[0]);expectSuccess("TurboScan end");activeTurboPid=0;}
        synchronized void turboCancel(int pid) throws IOException {auth();send(CMD_TURBO_CANCEL,ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(pid).array());expectSuccess("TurboScan cancel");} 

        private void ensureConnected() throws IOException {
            if (socket == null || !socket.isConnected() || socket.isClosed() || in == null || out == null)
                throw new IOException("Not connected to the PS5.");
        }

        private void send(int cmd, byte[] body) throws IOException {
            ensureConnected();
            if (body == null) throw new IOException("Protocol request body is missing.");
            if (body.length > MAX_REQUEST_BYTES) throw new IOException("Protocol request exceeds the 4 MiB safety limit.");
            try {
                ByteBuffer h = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
                h.putInt(MAGIC).putInt(cmd).putInt(body.length);
                out.write(h.array());
                if (body.length > 0) out.write(body);
                out.flush();
            } catch (IOException e) {
                close(); // A partial frame makes the stream unsafe to reuse.
                throw e;
            }
        }

        private byte[] readBytes(int n) throws IOException {
            if (n < 0 || n > MAX_RESPONSE_BYTES) {
                close();
                throw new IOException("Protocol response length is outside the 0–16 MiB safety limit: " + n);
            }
            byte[] b = new byte[n];
            try {
                in.readFully(b);
                return b;
            } catch (IOException e) {
                close(); // EOF/timeouts can leave a partially consumed frame; fail closed.
                throw new IOException("PS5 protocol response was interrupted; reconnect before retrying.", e);
            }
        }
        private int readIntLE() throws IOException {
            byte[] b = readBytes(4);
            return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt();
        }
        private long readLongLE() throws IOException {
            byte[] b = readBytes(8);
            return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getLong();
        }
        private int readUShortLE() throws IOException {
            byte[] b = readBytes(2);
            return (b[0] & 255) | ((b[1] & 255) << 8);
        }
        private void expectSuccess(String label) throws IOException {
            int status = readIntLE();
            if (status != SUCCESS) throw new IOException(label + " failed (status 0x" + Integer.toHexString(status).toUpperCase(Locale.US) + ")");
        }
        synchronized List<ProcessEntry> listProcesses() throws IOException {
            send(CMD_PROC_LIST, new byte[0]);
            expectSuccess("Process list");
            int count = readIntLE();
            if (count < 0 || count > 4096) throw new IOException("Invalid process count: " + count);
            byte[] raw = readBytes(count * 36);
            List<ProcessEntry> list = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int off = i * 36;
                String name = cString(raw, off, 32);
                int process = ByteBuffer.wrap(raw, off + 32, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                list.add(new ProcessEntry(name, process));
            }
            return list;
        }
        synchronized ProcInfo procInfo(int pid) throws IOException {
            ByteBuffer body = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(pid);
            send(CMD_PROC_INFO, body.array());
            expectSuccess("Process info");
            byte[] b = readBytes(188);
            ProcInfo info = new ProcInfo();
            info.pid = ByteBuffer.wrap(b, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            info.name = cString(b, 4, 40);
            info.path = cString(b, 44, 64);
            info.titleId = cString(b, 108, 16);
            info.contentId = cString(b, 124, 64);
            return info;
        }
        synchronized List<MapEntry> maps(int pid) throws IOException {
            ByteBuffer body = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(pid);
            send(CMD_PROC_MAPS, body.array());
            expectSuccess("Process maps");
            int count = readIntLE();
            if (count < 0 || count > 20000) throw new IOException("Invalid map count: " + count);
            byte[] b = readBytes(count * 58);
            List<MapEntry> result = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int o = i * 58;
                MapEntry e = new MapEntry();
                e.name = cString(b, o, 32);
                ByteBuffer bb = ByteBuffer.wrap(b, o + 32, 26).order(ByteOrder.LITTLE_ENDIAN);
                e.start = bb.getLong(); e.end = bb.getLong(); e.offset = bb.getLong(); e.prot = bb.getShort() & 0xffff;
                result.add(e);
            }
            return result;
        }
        private void hijackGate() throws IOException {
            long wait=150-(System.currentTimeMillis()-lastHijackMillis);
            if(wait>0)try{Thread.sleep(wait);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Remote operation interrupted before dispatch.",e);}
        }
        private void hijackDone(){lastHijackMillis=System.currentTimeMillis();}
        synchronized long alloc(int pid,int length) throws IOException {
            if(length<=0||length>1024*1024)throw new IOException("Remote allocation must be 1 byte–1 MiB.");
            hijackGate();ensureConnected();socket.setSoTimeout(30000);
            try{
                send(CMD_PROC_ALLOC,ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(pid).putInt(length).array());
                expectSuccess("Remote allocation");return readLongLE();
            }finally{if(isConnected())socket.setSoTimeout(15000);hijackDone();}
        }
        synchronized void free(int pid,long address,int length) throws IOException {
            if(length<=0||length>1024*1024)throw new IOException("Remote free size must be 1 byte–1 MiB.");
            hijackGate();
            try{
                send(CMD_PROC_FREE,ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(pid).putLong(address).putInt(length).array());
                expectSuccess("Remote free");
                TrampolinePool pool=trampolinePools.get(pid);
                if(pool!=null&&pool.base==address)trampolinePools.remove(pid);
            }finally{hijackDone();}
        }
        synchronized void releaseTrampolinePools() throws IOException {
            IOException first=null;
            List<Map.Entry<Integer,TrampolinePool>> pools=new ArrayList<>(trampolinePools.entrySet());
            for(Map.Entry<Integer,TrampolinePool> entry:pools){
                try{free(entry.getKey(),entry.getValue().base,TRAMP_POOL);}
                catch(IOException e){if(first==null)first=e;else first.addSuppressed(e);}
            }
            if(first!=null)throw first;
        }
        private byte[] buildCallTrampoline(long target){
            byte[] prefix=parseHex("49 89 E3 48 8D A4 24 00 F8 FF FF 48 83 E4 F0 41 53 41 53 48 81 EC 00 01 00 00 31 C0 49 BB");
            byte[] suffix=parseHex("41 FF D3 48 81 C4 00 01 00 00 41 5B 5C C3");
            ByteBuffer code=ByteBuffer.allocate(TRAMP_SLOT).order(ByteOrder.LITTLE_ENDIAN);
            code.put(prefix).putLong(target).put(suffix);
            while(code.hasRemaining())code.put((byte)0xCC);
            return code.array();
        }
        private long trampolineFor(int pid,long target) throws IOException {
            TrampolinePool pool=trampolinePools.get(pid);
            if(pool!=null&&pool.slots.containsKey(target))return pool.slots.get(target);
            if(pool==null||pool.used+TRAMP_SLOT>TRAMP_POOL){
                long base=alloc(pid,TRAMP_POOL);
                if(base==0)throw new IOException("Console returned a null trampoline allocation.");
                pool=new TrampolinePool(base);trampolinePools.put(pid,pool);
            }
            long address=pool.base+pool.used;byte[] code=buildCallTrampoline(target);
            write(pid,address,code);
            if(!Arrays.equals(code,read(pid,address,code.length)))throw new IOException("Call trampoline write verification failed.");
            pool.used+=TRAMP_SLOT;pool.slots.put(target,address);return address;
        }
        synchronized long callSafe(int pid,long target,long[] args) throws IOException {
            if(args==null||args.length!=6)throw new IOException("Remote calls require exactly six argument slots.");
            long entry=trampolineFor(pid,target);
            hijackGate();
            ByteBuffer body=ByteBuffer.allocate(68).order(ByteOrder.LITTLE_ENDIAN).putInt(pid).putLong(0L).putLong(entry);
            for(long arg:args)body.putLong(arg);
            ensureConnected();socket.setSoTimeout(30000);
            try{
                send(CMD_PROC_CALL,body.array());expectSuccess("Remote function call");
                byte[] response=readBytes(12);ByteBuffer result=ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN);
                int responsePid=result.getInt();long rax=result.getLong();
                if(responsePid!=pid)throw new IOException("Remote call response PID mismatch.");
                lastRemoteCallMillis=System.currentTimeMillis();return rax;
            }finally{if(isConnected())socket.setSoTimeout(15000);hijackDone();}
        }
        synchronized byte[] read(int pid, long address, int length) throws IOException {
            if (length <= 0 || length > 1024 * 1024) throw new IOException("Read size out of range.");
            if (Long.compareUnsigned(address + (long)length - 1L, address) < 0)
                throw new IOException("Memory read address range overflows.");
            ByteBuffer body = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
            body.putInt(pid).putLong(address).putInt(length);
            send(CMD_PROC_READ, body.array());
            expectSuccess("Memory read");
            return readBytes(length);
        }
        synchronized List<byte[]> readMulti(int pid,List<MultiReadEntry> entries) throws IOException {
            if(entries==null||entries.isEmpty())return new ArrayList<>();
            if(entries.size()>0xFFFF)throw new IOException("Bulk read supports at most 65,535 entries.");
            long total=0;
            ByteBuffer descriptors=ByteBuffer.allocate(entries.size()*12).order(ByteOrder.LITTLE_ENDIAN);
            for(MultiReadEntry entry:entries){
                if(entry.length<=0||entry.length>1024*1024)throw new IOException("Bulk read entry size must be 1 byte–1 MiB.");
                total+=entry.length;
                if(total>MAX_RESPONSE_BYTES)throw new IOException("Bulk read batch exceeds the 16 MiB response limit.");
                descriptors.putLong(entry.address).putInt(entry.length);
            }
            auth();
            send(CMD_PROC_READ_MULTI,ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(pid).putInt(entries.size()).array());
            expectSuccess("Bulk read acknowledgement");
            try {
                out.write(descriptors.array());out.flush();
                byte[] raw=readBytes((int)total);
                if(readLongLE()!=-1L)throw new IOException("Bulk read response is missing its end marker.");
                List<byte[]> result=new ArrayList<>();int offset=0;
                for(MultiReadEntry entry:entries){result.add(Arrays.copyOfRange(raw,offset,offset+entry.length));offset+=entry.length;}
                return result;
            } catch(IOException e) {
                close(); // Missing/partial sentinel or payload means the stream cannot be reused safely.
                throw e;
            }
        }

        synchronized byte[] writeMulti(int pid,List<MultiWriteEntry> entries) throws IOException {
            if(entries==null||entries.isEmpty())return new byte[0];
            if(entries.size()>0xFFFF)throw new IOException("Bulk write supports at most 65,535 entries.");
            ByteBuffer request=ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(pid).putInt(entries.size()).putInt(1); // request per-entry status bytes
            send(CMD_PROC_WRITE_MULTI,request.array());
            expectSuccess("Bulk write acknowledgement");
            try {
                for(MultiWriteEntry entry:entries){
                    if(entry.data==null||entry.data.length==0||entry.data.length>1024*1024)
                        throw new IOException("Bulk write entry size must be 1 byte–1 MiB.");
                    ByteBuffer header=ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                        .putLong(entry.address).putInt(entry.data.length);
                    out.write(header.array());
                    out.write(entry.data);
                }
                out.flush();
                byte[] statuses=readBytes(entries.size());
                expectSuccess("Bulk write completion");
                return statuses;
            } catch(IOException e) {
                close(); // The streamed entry list may be incomplete; never reuse this connection.
                throw e;
            }
        }

        synchronized void write(int pid, long address, byte[] data) throws IOException {
            if (data == null) throw new IOException("Memory write data is missing.");
            if (data.length == 0) return;
            if (data.length > 1024 * 1024) throw new IOException("Memory write size exceeds the 1 MiB command limit.");
            if (Long.compareUnsigned(address + (long)data.length - 1L, address) < 0)
                throw new IOException("Memory write address range overflows.");
            ByteBuffer body = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
            body.putInt(pid).putLong(address).putInt(data.length);
            send(CMD_PROC_WRITE, body.array());
            expectSuccess("Memory write acknowledgement");
            out.write(data);
            out.flush();
            expectSuccess("Memory write");
        }
        synchronized byte[] readStack(int pid,long rbp,long rsp,int depth) throws IOException {
            if(depth<1||depth>64)throw new IOException("Stack depth must be 1–64.");
            ByteBuffer body=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
            body.putInt(pid).putLong(rbp).putLong(rsp).putInt(depth);
            send(CMD_PROC_READ_STACK,body.array());expectSuccess("Call stack");
            int length=readIntLE();
            if(length<4||length>1024*1024)throw new IOException("Invalid call-stack response length: "+length);
            return readBytes(length);
        }
        synchronized List<DisasmEntry> disassemble(int pid, long address, int length, int max) throws IOException {
            ByteBuffer body = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
            body.putInt(pid).putLong(address).putInt(length).putInt(max);
            send(CMD_PROC_DISASM_REGION, body.array());
            expectSuccess("Disassembly");
            List<DisasmEntry> out = new ArrayList<>();
            while (true) {
                byte[] raw = readBytes(32);
                boolean end = true; for (byte v : raw) if ((v & 255) != 255) { end = false; break; }
                if (end) break;
                ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
                long addr = b.getLong(); b.getLong(); b.getLong();
                int len = b.get() & 255; int kind = b.get() & 255;
                b.get(); b.get(); b.get(); b.get(); b.getShort();
                out.add(new DisasmEntry(addr, len, kind, new byte[0]));
                if (out.size() >= max) break;
            }
            // The disassembly stream is fully drained before issuing ordinary read commands.
            List<DisasmEntry> withBytes = new ArrayList<>();
            for (DisasmEntry e : out) withBytes.add(new DisasmEntry(e.address, e.length, e.kind, readMemoryForDisasm(pid, e.address, e.length)));
            return withBytes;
        }
        private byte[] readMemoryForDisasm(int pid, long addr, int len) throws IOException {
            // A disassembly operation cannot interleave another command on this socket.
            ByteBuffer body = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(pid).putLong(addr).putInt(len);
            send(CMD_PROC_READ, body.array()); expectSuccess("Instruction bytes"); return readBytes(len);
        }
        synchronized List<Long> findXrefs(int pid, long address, int length, long target) throws IOException {
            ByteBuffer body = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
            body.putInt(pid).putLong(address).putInt(length).putLong(target);
            send(CMD_PROC_FIND_XREFS_TO, body.array()); expectSuccess("Find references");
            List<Long> out = new ArrayList<>();
            while (true) { long v = readLongLE(); if (v == -1L) break; out.add(v); if (out.size() >= 10000) break; }
            return out;
        }
        synchronized void debugAttach(int pid, boolean attach) throws IOException {
            if (attach) {
                send(CMD_DEBUG_ATTACH, ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(pid).array());
                expectSuccess("Debug attach");
            } else { send(CMD_DEBUG_DETACH, new byte[0]); expectSuccess("Debug detach"); }
        }
        synchronized void breakpoint(int index, boolean enabled, long address) throws IOException {
            if (index < 0 || index > 29) throw new IOException("Breakpoint slot must be 0–29.");
            ByteBuffer b = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
            b.putInt(index).putInt(enabled ? 1 : 0).putLong(address);
            send(CMD_DEBUG_SET_BREAKPOINT, b.array()); expectSuccess("Breakpoint");
        }
        synchronized void watchpoint(int index, boolean enabled, long address, int length, int type) throws IOException {
            if (index < 0 || index > 3) throw new IOException("Watchpoint slot must be 0–3.");
            int lenCode;
            if (!enabled) lenCode=0;
            else if(length==1)lenCode=0;
            else if(length==2)lenCode=1;
            else if(length==8)lenCode=2;
            else if(length==4)lenCode=3;
            else throw new IOException("Hardware watchpoint size must be 1, 2, 4 or 8 bytes.");
            if(enabled && type!=1 && type!=3)throw new IOException("Watchpoint mode must be write (1) or read/write (3).");
            if(enabled && Long.remainderUnsigned(address,length)!=0)throw new IOException("Hardware watchpoint address is not aligned to its size.");
            ByteBuffer b = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
            b.putInt(index).putInt(enabled ? 1 : 0).putInt(lenCode).putInt(enabled?type:0).putLong(address);
            send(CMD_DEBUG_SET_WATCHPOINT, b.array()); expectSuccess("Watchpoint");
        }
        synchronized List<Integer> threadList() throws IOException {
            send(CMD_DEBUG_GET_THREAD_LIST, new byte[0]); expectSuccess("Thread list");
            int n = readIntLE(); if (n < 0 || n > 4096) throw new IOException("Invalid thread count.");
            List<Integer> out = new ArrayList<>(); for (int i=0;i<n;i++) out.add(readIntLE()); return out;
        }
        synchronized byte[] getRegisters(int tid) throws IOException {
            send(CMD_DEBUG_GETREGS, ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(tid).array());
            expectSuccess("Get registers"); return readBytes(176);
        }
        synchronized void setRegisters(int tid,byte[] registers) throws IOException {
            if(registers==null||registers.length!=176)throw new IOException("General register image must be exactly 176 bytes.");
            ByteBuffer h=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(tid).putInt(registers.length);
            send((int)0xBDBB0009,h.array());expectSuccess("Set registers acknowledgement");
            out.write(registers);out.flush();expectSuccess("Set registers completion");
        }
        synchronized byte[] getFpuRegisters(int tid) throws IOException {
            send((int)0xBDBB000A,ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(tid).array());
            expectSuccess("Get FPU registers");return readBytes(832);
        }
        synchronized void setFpuRegisters(int tid,byte[] registers) throws IOException {
            if(registers==null||registers.length!=832)throw new IOException("FPU/YMM register image must be exactly 832 bytes.");
            ByteBuffer h=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(tid).putInt(registers.length);
            send((int)0xBDBB000B,h.array());expectSuccess("Set FPU registers acknowledgement");
            out.write(registers);out.flush();expectSuccess("Set FPU registers completion");
        }
        synchronized void debugStep() throws IOException {
            send(CMD_DEBUG_STEP, new byte[0]); expectSuccess("Single step");
        }
        synchronized void debugStepThread(int tid) throws IOException {
            send(CMD_DEBUG_STEP_THREAD,ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(tid).array());
            expectSuccess("Thread single step");
        }
        synchronized void debugContinue(int action) throws IOException {
            if(action<0||action>1)throw new IOException("Debug continue action must be 0 (resume) or 1 (stop).");
            send(CMD_DEBUG_CONTINUE,ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(action).array());
            expectSuccess(action==1?"Process stop":"Process resume");
        }
        private static String cString(byte[] b, int off, int len) {
            int end = off;
            while (end < off + len && b[end] != 0) end++;
            return new String(b, off, end-off, StandardCharsets.UTF_8).trim();
        }
    }
}
