package com.skythedevil.ps5debugmobile;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
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

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService cancelWorker = Executors.newSingleThreadExecutor();
    private PS5Client client;
    private boolean connected = false;
    private List<ProcessEntry> processes = new ArrayList<>();
    private List<MapEntry> maps = new ArrayList<>();
    private int selectedPid = -1;
    private JSONObject gameFile;
    private final Map<Integer, List<UndoPatch>> enabledCheats = new HashMap<>();

    private EditText host, port, pid, readAddress, readLength, writeAddress, writeBytes;
    private EditText scanAddress, scanLength, scanBytes;
    private EditText turboValue, turboResultStart, turboResultCount;
    private long turboResultTotal=0;
    private Spinner turboType;
    private Spinner turboCompare;
    private TextView turboResult;
    private LinearLayout turboRows;
    private List<PS5Client.TurboResult> lastTurboResults = new ArrayList<>();
    private PS5Client scanClient;
    private boolean turboActive = false;
    private int turboValueType = 4;
    private int turboValueLength = 4;
    private EditText gameName, titleId, gameVersion, cheatName, cheatAddress, cheatBytes;
    private TextView connectionStatus, processInfo, memoryResult, scanResult, cheatStatus, mapResult, debugResult, disasmResult;
    private EditText mapFilter, disasmAddress, disasmLength, xrefTarget, breakpointIndex, breakpointAddress, watchIndex, watchAddress, threadId;
    private Spinner processSpinner;
    private LinearLayout cheatList;
    private Button connectButton;
    private EditText addressEntryName, addressEntryAddress, addressEntryBytes;
    private LinearLayout addressRows;
    private final List<AddressEntry> addressEntries = new ArrayList<>();
    private final Handler addressHandler = new Handler(Looper.getMainLooper());
    private final Runnable addressFreezeTick = new Runnable() { @Override public void run() { refreshFrozenAddresses(); if (!addressEntries.isEmpty()) addressHandler.postDelayed(this, 1000); } };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        buildUi();
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

        writeAddress = field("Write address", "0x0");
        writeBytes = field("Bytes to write (hex)", "90 90 90");
        page.addView(writeAddress); page.addView(writeBytes);
        Button write = button("WRITE MEMORY", PANEL);
        page.addView(write);
        write.setOnClickListener(v -> writeMemory());

        section(page, "03B  ADDRESS LIST & FREEZE", "Save memory addresses, refresh values, edit bytes and freeze selected entries at one-second intervals.");
        addressEntryName=field("Entry name", "Money");
        addressEntryAddress=field("Address", "0x0");
        addressEntryBytes=field("Value bytes (hex)", "00 00 00 00");
        page.addView(addressEntryName); page.addView(addressEntryAddress); page.addView(addressEntryBytes);
        LinearLayout addressActions=new LinearLayout(this); addressActions.setOrientation(LinearLayout.HORIZONTAL);
        Button addAddress=button("ADD ADDRESS", ACCENT), refreshAddresses=button("REFRESH ALL", PANEL);
        addressActions.addView(addAddress,new LinearLayout.LayoutParams(0,-2,1)); addressActions.addView(refreshAddresses,new LinearLayout.LayoutParams(0,-2,1)); page.addView(addressActions);
        addressRows=new LinearLayout(this); addressRows.setOrientation(LinearLayout.VERTICAL); page.addView(addressRows);
        addAddress.setOnClickListener(v->addAddressEntry());
        refreshAddresses.setOnClickListener(v->refreshAddressEntries());
        loadAddressEntries();

        section(page, "04  EXACT BYTE SCAN", "Scans a chosen range for an exact byte pattern. Each scan is capped at 16 MiB to keep mobile memory and network use bounded.");
        scanAddress = field("Start address", "0x0");
        scanLength = field("Range size (max 16 MiB)", "1048576");
        scanBytes = field("Pattern (hex)", "DE AD BE EF");
        page.addView(scanAddress); page.addView(scanLength); page.addView(scanBytes);
        Button scan = button("SCAN RANGE", PANEL);
        page.addView(scan);
        scanResult = text("No scan performed.", 12, MUTED, false);
        scanResult.setTypeface(Typeface.MONOSPACE);
        page.addView(scanResult);
        scan.setOnClickListener(v -> scanRange());

        section(page, "04B  TURBO VALUE SCANNER", "Uses the payload's server-side TurboScan engine on a separate connection. Start with an exact value, refine with Next Scan, then view results.");
        turboType = new Spinner(this);
        String[] turboTypes = {"Byte (unsigned)", "Byte (signed)", "2 bytes (unsigned)", "2 bytes (signed)", "4 bytes (unsigned)", "4 bytes (signed)", "8 bytes (unsigned)", "8 bytes (signed)", "Float", "Double"};
        turboType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, turboTypes));
        turboType.setSelection(4);
        page.addView(turboType);
        turboCompare = new Spinner(this);
        String[] compareLabels = {"Exact value", "Fuzzy (±1)", "Bigger than", "Smaller than", "Between low/high", "Increased", "Increased by", "Decreased", "Decreased by", "Changed", "Unchanged", "Unknown initial value"};
        turboCompare.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, compareLabels));
        page.addView(turboCompare);
        turboValue = field("Value (for Between use low,high)", "100"); page.addView(turboValue);
        turboResultStart=field("Result page start index", "0"); turboResultCount=field("Results per page (1–500)", "100");
        page.addView(turboResultStart); page.addView(turboResultCount);
        Button turboFirst = button("TURBO FIRST SCAN", ACCENT);
        Button turboNext = button("NEXT SCAN (EXACT VALUE)", PANEL);
        Button turboGet = button("LOAD FIRST 100 RESULTS", PANEL);
        Button turboEnd = button("END SCAN SESSION", PANEL);
        Button turboCancel = button("CANCEL RUNNING SCAN", PANEL);
        page.addView(turboFirst); page.addView(turboNext); page.addView(turboGet); page.addView(turboEnd); page.addView(turboCancel);
        turboResult = text("TurboScan not started. Requires payload TurboScan support.", 11, MUTED, false);
        turboResult.setTypeface(Typeface.MONOSPACE); page.addView(turboResult);
        turboRows=new LinearLayout(this); turboRows.setOrientation(LinearLayout.VERTICAL); page.addView(turboRows);
        turboFirst.setOnClickListener(v -> turboFirstScan());
        turboNext.setOnClickListener(v -> turboNextScan());
        turboGet.setOnClickListener(v -> turboLoadResults());
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

        section(page, "06  DEBUGGER", "Attach/detach, software breakpoints, hardware watchpoints, thread listing and register inspection. Full async stepping/event UI is still pending.");
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
        page.addView(watchIndex); page.addView(watchAddress);
        Button watchOn = button("SET WRITE WATCHPOINT", PANEL);
        Button watchOff = button("CLEAR WATCHPOINT", PANEL);
        page.addView(watchOn); page.addView(watchOff);
        Button attach = button("ATTACH DEBUGGER", ACCENT);
        Button detach = button("DETACH DEBUGGER", PANEL);
        Button threads = button("LIST THREADS", PANEL);
        threadId = field("Thread/LWP ID", "");
        Button regs = button("READ GENERAL REGISTERS", PANEL);
        Button step = button("SINGLE STEP PROCESS", PANEL);
        page.addView(attach); page.addView(detach); page.addView(threads); page.addView(threadId); page.addView(regs); page.addView(step);
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

        section(page, "07  CHEAT LIBRARY", "Imports and exports PS5 Debug Studio game JSON (ps5cheat/1). V1 runs fixed patch steps only.");
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
        fileButtons.addView(importFile, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(48), 1);
        right.setMargins(dp(8), 0, 0, 0);
        fileButtons.addView(exportFile, right);
        page.addView(fileButtons);
        cheatStatus = text("No game file loaded.", 12, MUTED, false);
        cheatStatus.setPadding(0, dp(10), 0, dp(6));
        page.addView(cheatStatus);
        cheatList = new LinearLayout(this);
        cheatList.setOrientation(LinearLayout.VERTICAL);
        page.addView(cheatList);
        create.setOnClickListener(v -> createGameFile());
        addPatch.setOnClickListener(v -> addPatchCheat());
        importFile.setOnClickListener(v -> importJson());
        exportFile.setOnClickListener(v -> exportJson());

        section(page, "08  ACTIVITY LOG", "Recent connection and operation results.");
        logView = text("Ready.", 11, MUTED, false);
        logView.setTypeface(Typeface.MONOSPACE);
        page.addView(logView);
    }

    private TextView logView;

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
        TextView d = text(detail, 11, MUTED, false);
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
        e.setTextSize(14);
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
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(0, dp(3), 0, dp(3));
        return t;
    }

    private Button button(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
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

    private void runAsync(String action, Callable<String> task, Consumer<String> onSuccess) {
        log(action + "…");
        worker.execute(() -> {
            try {
                String result = task.call();
                main.post(() -> onSuccess.accept(result));
            } catch (Exception ex) {
                String message = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                main.post(() -> {
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
                if (client != null) client.close();
                if (scanClient != null) scanClient.close();
                scanClient = null; turboActive = false;
                client = null;
                connected = false;
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
            refreshProcesses();
        });
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
            byte[] data = client.read(p, address, length);
            return "0x" + Long.toHexString(address).toUpperCase(Locale.US) + "\n" + hexDump(data, address);
        }, value -> memoryResult.setText(value));
    }

    private void writeMemory() {
        if (!connected) { toast("Connect to the PS5 first."); return; }
        runAsync("Writing memory", () -> {
            int p = currentPid();
            long address = resolveAddress(writeAddress.getText().toString().trim(), p);
            byte[] data = parseHex(writeBytes.getText().toString());
            if (data.length == 0 || data.length > 4096) throw new IllegalArgumentException("Write 1–4096 bytes.");
            client.write(p, address, data);
            return "Wrote " + data.length + " byte(s) at 0x" + Long.toHexString(address).toUpperCase(Locale.US);
        }, value -> {
            memoryResult.setText(value);
            log(value);
        });
    }


    private static final class AddressEntry {
        String name, address, bytes; boolean frozen;
        AddressEntry(String n,String a,String b,boolean f){name=n;address=a;bytes=b;frozen=f;}
    }
    private void loadAddressEntries() {
        addressEntries.clear();
        try {
            JSONArray arr=new JSONArray(getSharedPreferences("address_table",MODE_PRIVATE).getString("entries","[]"));
            for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);addressEntries.add(new AddressEntry(o.optString("name","Entry"),o.optString("address","0x0"),o.optString("bytes","00"),o.optBoolean("frozen",false)));}
        } catch(Exception e){log("Saved address table could not be loaded: "+e.getMessage());}
        renderAddressEntries();
        if(addressEntries.stream().anyMatch(e->e.frozen)) addressHandler.removeCallbacks(addressFreezeTick);
    }
    private void saveAddressEntries() {
        JSONArray arr=new JSONArray();
        for(AddressEntry e:addressEntries){JSONObject o=new JSONObject();try{o.put("name",e.name);o.put("address",e.address);o.put("bytes",e.bytes);o.put("frozen",e.frozen);}catch(JSONException ignored){}arr.put(o);}
        getSharedPreferences("address_table",MODE_PRIVATE).edit().putString("entries",arr.toString()).apply();
    }
    private void addAddressEntry() {
        try {
            String n=addressEntryName.getText().toString().trim(),a=addressEntryAddress.getText().toString().trim(),b=toHex(parseHex(addressEntryBytes.getText().toString()));
            if(a.isEmpty()||b.isEmpty())throw new IllegalArgumentException("Enter an address and at least one byte.");
            addressEntries.add(new AddressEntry(n.isEmpty()?"Entry "+(addressEntries.size()+1):n,a,b,false));
            saveAddressEntries();renderAddressEntries();log("Address added: "+a);
        } catch(Exception e){toast(e.getMessage());}
    }
    private void renderAddressEntries() {
        if(addressRows==null)return;
        addressRows.removeAllViews();
        for(int i=0;i<addressEntries.size();i++){
            final int index=i;AddressEntry e=addressEntries.get(i);
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(8),dp(6),dp(8),dp(6));card.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL));
            TextView label=text(e.name+"  "+e.address+"\n"+e.bytes+(e.frozen?"  [FROZEN]":""),12,TEXT,true);label.setTypeface(Typeface.MONOSPACE);card.addView(label);
            LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
            Button read=button("READ",PANEL),edit=button("EDIT",PANEL),freeze=button(e.frozen?"UNFREEZE":"FREEZE",e.frozen?GREEN:PANEL),remove=button("REMOVE",PANEL);
            for(Button b:new Button[]{read,edit,freeze,remove})actions.addView(b,new LinearLayout.LayoutParams(0,-2,1));
            card.addView(actions);addressRows.addView(card,new LinearLayout.LayoutParams(-1,-2));
            read.setOnClickListener(v->readAddressEntry(index));
            edit.setOnClickListener(v->editAddressEntry(index));
            freeze.setOnClickListener(v->{addressEntries.get(index).frozen=!addressEntries.get(index).frozen;saveAddressEntries();renderAddressEntries();if(addressEntries.stream().anyMatch(x->x.frozen)){addressHandler.removeCallbacks(addressFreezeTick);addressHandler.post(addressFreezeTick);}else addressHandler.removeCallbacks(addressFreezeTick);});
            remove.setOnClickListener(v->{addressEntries.remove(index);saveAddressEntries();renderAddressEntries();});
        }
    }
    private void readAddressEntry(int index) {
        if(!connected){toast("Connect to the PS5 first.");return;}
        AddressEntry e=addressEntries.get(index);
        runAsync("Reading address entry",()->{long a=resolveAddress(e.address,currentPid());byte[] data=client.read(currentPid(),a,parseHex(e.bytes).length);e.bytes=toHex(data);saveAddressEntries();return e.name+": "+e.bytes;},r->{renderAddressEntries();log(r);});
    }
    private void editAddressEntry(int index) {
        AddressEntry e=addressEntries.get(index);LinearLayout box=new LinearLayout(this);box.setPadding(dp(12),0,dp(12),0);box.setOrientation(LinearLayout.VERTICAL);
        EditText name=field("Name",e.name),addr=field("Address",e.address),bytes=field("Value bytes (hex)",e.bytes);box.addView(name);box.addView(addr);box.addView(bytes);
        new android.app.AlertDialog.Builder(this).setTitle("Edit address entry").setView(box).setNegativeButton("Cancel",null).setPositiveButton("SAVE",(d,w)->{try{parseHex(bytes.getText().toString());e.name=name.getText().toString().trim();e.address=addr.getText().toString().trim();e.bytes=toHex(parseHex(bytes.getText().toString()));saveAddressEntries();renderAddressEntries();}catch(Exception ex){toast(ex.getMessage());}}).show();
    }
    private void refreshAddressEntries() {
        if(!connected){toast("Connect to the PS5 first.");return;}
        runAsync("Refreshing address table",()->{for(AddressEntry e:addressEntries){long a=resolveAddress(e.address,currentPid());e.bytes=toHex(client.read(currentPid(),a,parseHex(e.bytes).length));}saveAddressEntries();return "Refreshed "+addressEntries.size()+" address entries.";},r->{renderAddressEntries();log(r);});
    }
    private void refreshFrozenAddresses() {
        if(!connected)return;
        List<AddressEntry> frozen=new ArrayList<>();for(AddressEntry e:addressEntries)if(e.frozen)frozen.add(e);
        if(frozen.isEmpty())return;
        worker.execute(()->{try{for(AddressEntry e:frozen){long a=resolveAddress(e.address,currentPid());client.write(currentPid(),a,parseHex(e.bytes));}}catch(Exception ex){log("Freeze write failed: "+ex.getMessage());}});
    }

    private void scanRange() {
        if (!connected) { toast("Connect to the PS5 first."); return; }
        runAsync("Scanning memory range", () -> {
            int p = currentPid();
            long start = resolveAddress(scanAddress.getText().toString().trim(), p);
            int length = Integer.decode(scanLength.getText().toString().trim());
            byte[] pattern = parseHex(scanBytes.getText().toString());
            if (pattern.length == 0 || pattern.length > 64) throw new IllegalArgumentException("Pattern must contain 1–64 bytes.");
            if (length <= 0 || length > SCAN_MAX) throw new IllegalArgumentException("Scan range must be between 1 byte and 16 MiB.");
            List<Long> matches = new ArrayList<>();
            int chunkSize = 64 * 1024;
            byte[] tail = new byte[0];
            long scanned = 0;
            while (scanned < length && matches.size() < 200) {
                int n = (int)Math.min(chunkSize, length - scanned);
                byte[] block = client.read(p, start + scanned, n);
                byte[] combined = new byte[tail.length + block.length];
                System.arraycopy(tail, 0, combined, 0, tail.length);
                System.arraycopy(block, 0, combined, tail.length, block.length);
                long combinedBase = start + scanned - tail.length;
                for (int i = 0; i <= combined.length - pattern.length; i++) {
                    boolean same = true;
                    for (int j = 0; j < pattern.length; j++) if (combined[i+j] != pattern[j]) { same = false; break; }
                    if (same) {
                        long at = combinedBase + i;
                        if (at >= start + scanned - tail.length) matches.add(at);
                        if (matches.size() >= 200) break;
                    }
                }
                int keep = Math.min(pattern.length - 1, combined.length);
                tail = Arrays.copyOfRange(combined, combined.length - keep, combined.length);
                scanned += n;
            }
            StringBuilder out = new StringBuilder();
            out.append("Scanned ").append(scanned).append(" bytes from 0x").append(Long.toHexString(start).toUpperCase(Locale.US));
            out.append("\nPattern: ").append(toHex(pattern)).append("\nMatches: ").append(matches.size());
            for (long m : matches) out.append("\n0x").append(Long.toHexString(m).toUpperCase(Locale.US));
            if (matches.size() == 200) out.append("\n(showing first 200)");
            return out.toString();
        }, value -> scanResult.setText(value));
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
            long count=compare==11?scanClient.turboUnknownFirstScan(p,start,length,type):scanClient.turboFirstScan(p,start,length,type,compare,value);
            turboActive=true; turboValueType=type; turboValueLength=(type==4||type==5?4:type==6||type==7||type==9?8:type==8?4:type==2||type==3?2:1); turboResultTotal=count;
            return "TurboScan started. Results: "+count+"\\nRange: 0x"+Long.toHexString(start)+" – 0x"+Long.toHexString(start+(long)length);
        },result->turboResult.setText(result));
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
        },result->turboResult.setText(result));
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
            Button edit=button("EDIT FOUND VALUE (HEX)",PANEL);row.addView(edit);edit.setOnClickListener(v->editTurboResult(item.address,item.current));
        }
    }
    private void editTurboResult(long address,byte[] current) {
        if(!connected){toast("Connect to the PS5 first.");return;}
        EditText value=new EditText(this);value.setSingleLine(true);value.setText(toHex(current));value.setTextColor(TEXT);value.setHintTextColor(MUTED);
        new android.app.AlertDialog.Builder(this).setTitle("Edit memory at 0x"+Long.toHexString(address).toUpperCase(Locale.US)).setMessage("Enter exactly "+current.length+" byte(s) in hexadecimal.").setView(value).setNegativeButton("Cancel",null).setPositiveButton("WRITE",(d,w)->{
            try {byte[] patch=parseHex(value.getText().toString());if(patch.length!=current.length)throw new IllegalArgumentException("New value must be exactly "+current.length+" byte(s).");
                runAsync("Editing found memory value",()->{client.write(currentPid(),address,patch);return "Updated memory at 0x"+Long.toHexString(address).toUpperCase(Locale.US);},result->{log(result);});
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

    private void debugAttach(boolean attach) {
        if (!connected) { toast("Connect to the PS5 first."); return; }
        runAsync(attach ? "Attaching debugger" : "Detaching debugger", () -> {
            client.debugAttach(currentPid(), attach);
            return attach ? "Debugger attached. The payload should connect back to the phone on TCP 755; async event UI is pending." : "Debugger detached.";
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
        runAsync(enabled ? "Setting watchpoint" : "Clearing watchpoint", () -> {
            int slot = Integer.parseInt(watchIndex.getText().toString().trim());
            long address = resolveAddress(watchAddress.getText().toString().trim(), currentPid());
            client.watchpoint(slot, enabled, address, enabled ? 8 : 0, enabled ? 1 : 0);
            return (enabled ? "8-byte write watchpoint set" : "Watchpoint cleared") + " at slot " + slot;
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
            String[] names = {"r15","r14","r13","r12","r11","r10","r9","r8","rdi","rsi","rbp","rbx","rdx","rcx","rsp","rip","rflags"};
            StringBuilder b = new StringBuilder();
            for (int i=0;i<names.length;i++) b.append(String.format(Locale.US, "%-7s %016X\\n", names[i], ByteBuffer.wrap(r, i*8, 8).order(ByteOrder.LITTLE_ENDIAN).getLong()));
            return b.toString();
        }, debugResult::setText);
    }

    private void debugStep() {
        if (!connected) { toast("Connect to the PS5 first."); return; }
        runAsync("Single stepping process", () -> { client.debugStep(); return "Single-step command accepted."; }, debugResult::setText);
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
        i.setType("application/json");
        startActivityForResult(i, REQ_IMPORT);
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
            if (requestCode == REQ_IMPORT) {
                String json = readUri(uri);
                JSONObject imported = new JSONObject(json);
                if (!imported.has("game") || !imported.has("cheats")) throw new JSONException("Not a PS5 Debug Studio game cheat JSON file.");
                gameFile = imported;
                gameName.setText(imported.optString("game"));
                titleId.setText(imported.optString("title_id"));
                gameVersion.setText(imported.optString("version"));
                refreshCheatList();
                log("Imported " + imported.getJSONArray("cheats").length() + " cheat(s).");
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
            return out.toString("UTF-8");
        }
    }

    private void refreshCheatList() {
        cheatList.removeAllViews();
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
            TextView name = text(c.optString("name", "Unnamed cheat"), 14, TEXT, true);
            row.addView(name);
            TextView detail = text(c.optString("mode", "toggle").toUpperCase(Locale.US) + "  •  " +
                    (c.optJSONArray("steps") == null ? 0 : c.optJSONArray("steps").length()) + " step(s)", 11, MUTED, false);
            row.addView(detail);
            Button apply = button("APPLY / RESTORE", ACCENT);
            row.addView(apply);
            apply.setOnClickListener(v -> toggleCheat(idx, c));
        }
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
                List<UndoPatch> undo = enabledCheats.remove(index);
                Collections.reverse(undo);
                for (UndoPatch u : undo) client.write(p, u.address, u.original);
                return "Restored " + cheat.optString("name") + ".";
            }
            List<UndoPatch> undo = new ArrayList<>();
            JSONArray steps = cheat.optJSONArray("steps");
            if (steps == null) throw new IllegalArgumentException("Cheat has no steps.");
            for (int n = 0; n < steps.length(); n++) {
                JSONObject step = steps.getJSONObject(n);
                String op = step.optString("op", "").toLowerCase(Locale.US);
                if (!op.equals("patch")) throw new IllegalArgumentException("V1 mobile supports fixed 'patch' steps only. Unsupported step: " + op);
                if (!"hex".equalsIgnoreCase(step.optString("type", "hex"))) throw new IllegalArgumentException("Only hex patch steps are supported in V1.");
                long address = resolveAddress(step.getString("addr"), p);
                byte[] patch = parseHex(step.getString("value"));
                if (patch.length == 0) throw new IllegalArgumentException("Empty patch step.");
                byte[] old = client.read(p, address, patch.length);
                client.write(p, address, patch);
                undo.add(new UndoPatch(address, old));
            }
            enabledCheats.put(index, undo);
            return "Applied " + cheat.optString("name") + ".";
        }, result -> {
            log(result);
            refreshCheatList();
        });
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
        super.onDestroy();
        worker.shutdownNow();
        cancelWorker.shutdownNow();
        if (client != null) client.close();
        if (scanClient != null) scanClient.close();
    }

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
        private static final int CMD_VERSION = (int)0xBD000001;
        private static final int CMD_TURBO_CAPS = (int)0xBDAACC10, CMD_TURBO_START = (int)0xBDAACC11, CMD_TURBO_COUNT = (int)0xBDAACC12, CMD_TURBO_GET = (int)0xBDAACC13, CMD_TURBO_END = (int)0xBDAACC14, CMD_TURBO_CANCEL = (int)0xBDAACC17, CMD_PROC_AUTH = (int)0xBDAACCFF;
        private boolean authenticated=false;
        private int activeTurboPid=0, activeTurboType=4, turboEngines=0;
        private static final int CMD_PROC_LIST = (int)0xBDAA0001;
        private static final int CMD_PROC_READ = (int)0xBDAA0002;
        private static final int CMD_PROC_WRITE = (int)0xBDAA0003;
        private static final int CMD_PROC_MAPS = (int)0xBDAA0004;
        private static final int CMD_PROC_INFO = (int)0xBDAA000A;
        private static final int CMD_PROC_DISASM_REGION = (int)0xBDAA0020;
        private static final int CMD_PROC_FIND_XREFS_TO = (int)0xBDAA0022;
        private static final int CMD_DEBUG_ATTACH = (int)0xBDBB0001;
        private static final int CMD_DEBUG_DETACH = (int)0xBDBB0002;
        private static final int CMD_DEBUG_SET_BREAKPOINT = (int)0xBDBB0003;
        private static final int CMD_DEBUG_SET_WATCHPOINT = (int)0xBDBB0004;
        private static final int CMD_DEBUG_GET_THREAD_LIST = (int)0xBDBB0005;
        private static final int CMD_DEBUG_GETREGS = (int)0xBDBB0008;
        private static final int CMD_DEBUG_STEP = (int)0xBDBB0012;
        private Socket socket;
        private DataInputStream in;
        private OutputStream out;

        synchronized String connect(String host, int port) throws Exception {
            close();
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
        }

        synchronized void close() {
            try { if (socket != null) socket.close(); } catch (Exception ignored) {}
            socket = null; in = null; out = null; authenticated=false;
        }
        synchronized boolean isConnected(){return socket!=null&&socket.isConnected()&&!socket.isClosed();}
        static final class TurboCaps { int version,engines,threads; }
        static final class TurboResult { long address; byte[] current,previous; }
        synchronized TurboCaps turboCaps() throws IOException {
            if(!isConnected()) throw new IOException("Not connected.");
            socket.setSoTimeout(4000);
            try { send(CMD_TURBO_CAPS,new byte[0]); int st=readIntLE(); if(st!=SUCCESS)return null; byte[] raw=readBytes(16); ByteBuffer b=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN); TurboCaps c=new TurboCaps(); c.version=b.getInt();c.engines=b.getInt();c.threads=b.getInt();b.getInt();turboEngines=c.engines;return c; }
            catch(java.net.SocketTimeoutException e){close();return null;}
            finally{if(isConnected())socket.setSoTimeout(15000);}
        }
        private static byte[] authKey(){long mask=0xFFFFFFFFL,s1=200,s2=300,s3=400,s4=500;byte[] o=new byte[256];for(int i=0;i<256;i++){s1=((s1<<18)&0xFFF80000L)^(((s1^(s1<<6))&mask)>>>13);s2=((s2<<2)&0xFFFFFFE0L)^(((s2^(s2<<2))&mask)>>>27);s3=((s3<<7)&0xFFFFF800L)^(((s3^(s3<<13))&mask)>>>21);s4=((s4<<13)&0xFFF00000L)^(((s4^(s4<<3))&mask)>>>12);o[i]=(byte)(s1^s2^s3^s4);}return o;}
        synchronized void auth() throws IOException {if(authenticated)return;ByteBuffer h=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(0xBB40E64D).putInt(2);send(CMD_PROC_AUTH,h.array());expectSuccess("TurboScan auth");int n=readUShortLE();if(n>4096)throw new IOException("Invalid auth challenge.");byte[] ch=readBytes(n),key=authKey();for(int i=0;i<n;i++)ch[i]=(byte)(ch[i]^key[i&255]);out.write(ch);out.flush();expectSuccess("TurboScan auth response");authenticated=true;}
        synchronized long turboFirstScan(int pid,long start,int length,int type,int compare,byte[] value) throws IOException {
            auth(); activeTurboPid=pid;activeTurboType=type; socket.setSoTimeout(120000);
            ByteBuffer h=ByteBuffer.allocate(27).order(ByteOrder.LITTLE_ENDIAN);h.putInt(pid).putLong(start).putInt(length).put((byte)type).put((byte)compare).put((byte)1).putInt(value.length).putInt(0x12);
            send(CMD_TURBO_START,h.array());expectSuccess("TurboScan start");out.write(value);out.flush();expectSuccess("TurboScan setup");
            ByteBuffer seg=ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(1).putLong(start).putInt(length);out.write(seg.array());out.flush();
            byte[] raw=readBytes(12);ByteBuffer r=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);int stored=r.getInt();long count=r.getLong();
            if(stored==0){if(readLongLE()!=-1L)throw new IOException("Unexpected TurboScan decline response.");expectSuccess("TurboScan decline");throw new IOException("Too many matches for the payload result list. Narrow the range.");}
            expectSuccess("TurboScan completion");socket.setSoTimeout(15000);return count;
        }
        synchronized long turboUnknownFirstScan(int pid,long start,int length,int type) throws IOException {
            if(type>9) throw new IOException("Unknown-value snapshots support numeric types only.");
            auth();activeTurboPid=pid;activeTurboType=type;socket.setSoTimeout(120000);
            int flags=0x04|0x10|((turboEngines&0x40)!=0?0x20:0);
            ByteBuffer h=ByteBuffer.allocate(27).order(ByteOrder.LITTLE_ENDIAN);h.putInt(pid).putLong(start).putInt(length).put((byte)type).put((byte)11).put((byte)1).putInt(0).putInt(flags);
            send(CMD_TURBO_START,h.array());expectSuccess("TurboScan snapshot start");expectSuccess("TurboScan snapshot setup");
            ByteBuffer seg=ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(1).putLong(start).putInt(length);out.write(seg.array());out.flush();
            long slots=readLongLE(),total=readLongLE();
            while(readLongLE()!=-1L){}
            int ok=readIntLE();long survivors=readLongLE();expectSuccess("TurboScan snapshot completion");socket.setSoTimeout(15000);
            if(ok==0)throw new IOException("Console could not store the memory snapshot; choose a smaller range.");
            return survivors;
        }
        synchronized long turboNextScan(int compare,byte[] value,int valueLength) throws IOException {
            auth();socket.setSoTimeout(120000);boolean needsValue=(compare==0||compare==2||compare==3||compare==4||compare==6||compare==8);
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
            if (socket == null || socket.isClosed()) throw new IOException("Not connected to the PS5.");
        }

        private void send(int cmd, byte[] body) throws IOException {
            ensureConnected();
            ByteBuffer h = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
            h.putInt(MAGIC).putInt(cmd).putInt(body.length);
            out.write(h.array());
            if (body.length > 0) out.write(body);
            out.flush();
        }

        private byte[] readBytes(int n) throws IOException {
            byte[] b = new byte[n];
            in.readFully(b);
            return b;
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
        synchronized byte[] read(int pid, long address, int length) throws IOException {
            if (length <= 0 || length > 1024 * 1024) throw new IOException("Read size out of range.");
            ByteBuffer body = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
            body.putInt(pid).putLong(address).putInt(length);
            send(CMD_PROC_READ, body.array());
            expectSuccess("Memory read");
            return readBytes(length);
        }
        synchronized void write(int pid, long address, byte[] data) throws IOException {
            if (data.length == 0) return;
            ByteBuffer body = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
            body.putInt(pid).putLong(address).putInt(data.length);
            send(CMD_PROC_WRITE, body.array());
            expectSuccess("Memory write acknowledgement");
            out.write(data);
            out.flush();
            expectSuccess("Memory write");
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
            // DR7 encodings: RW=1 write, LEN=2 eight-byte.
            int lenCode = length == 8 ? 2 : 0;
            int breakType = type == 1 ? 1 : 0;
            ByteBuffer b = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
            b.putInt(index).putInt(enabled ? 1 : 0).putInt(lenCode).putInt(breakType).putLong(address);
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
        synchronized void debugStep() throws IOException {
            send(CMD_DEBUG_STEP, new byte[0]); expectSuccess("Single step");
        }
        private static String cString(byte[] b, int off, int len) {
            int end = off;
            while (end < off + len && b[end] != 0) end++;
            return new String(b, off, end-off, StandardCharsets.UTF_8).trim();
        }
    }
}
