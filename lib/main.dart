import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

const native = MethodChannel('fakecall/native');
final navKey = GlobalKey<NavigatorState>();
bool callOpen = false;

void openCall(String name, Settings s) {
  if (callOpen) return;
  callOpen = true;
  navKey.currentState?.push(MaterialPageRoute(builder: (_) => CallPage(name: name, s: s)));
}

void main() => runApp(const App());

class Settings extends ChangeNotifier {
  List<String> names = ['Mom', 'Dad', 'Boss', 'Doctor'];
  String? selected; // null = random
  int delay = 10;
  int accent = 0xFF2E7D32;
  bool dark = true, ring = true, vibrate = true, volumeTrigger = true, screenOff = false;
  late SharedPreferences p;

  Future<void> load() async {
    p = await SharedPreferences.getInstance();
    names = p.getStringList('names') ?? names;
    selected = p.getString('selected');
    delay = p.getInt('delay') ?? delay;
    accent = p.getInt('accent') ?? accent;
    dark = p.getBool('dark') ?? dark;
    ring = p.getBool('ring') ?? ring;
    vibrate = p.getBool('vibrate') ?? vibrate;
    volumeTrigger = p.getBool('volumeTrigger') ?? volumeTrigger;
    screenOff = p.getBool('screenOff') ?? screenOff;
  }

  Future<void> sync() async {
    try {
      await native.invokeMethod('sync', {
        'names': names.join('\n'),
        'selected': selected ?? '',
        'delay': delay,
        'ring': ring,
        'vibrate': vibrate,
        'volumeTrigger': volumeTrigger,
        'screenOff': screenOff,
      });
    } catch (_) {}
  }

  void save() {
    p.setStringList('names', names);
    selected == null ? p.remove('selected') : p.setString('selected', selected!);
    p.setInt('delay', delay);
    p.setInt('accent', accent);
    p.setBool('dark', dark);
    p.setBool('ring', ring);
    p.setBool('vibrate', vibrate);
    p.setBool('volumeTrigger', volumeTrigger);
    p.setBool('screenOff', screenOff);
    sync();
    notifyListeners();
  }
}

class App extends StatefulWidget {
  const App({super.key});
  @override
  State<App> createState() => _AppState();
}

class _AppState extends State<App> {
  final s = Settings();
  bool ready = false;

  @override
  void initState() {
    super.initState();
    s.addListener(() => setState(() {}));
    native.setMethodCallHandler((c) async {
      if (c.method == 'incoming') openCall(c.arguments as String, s);
    });
    s.load().then((_) async {
      await s.sync();
      setState(() => ready = true);
      WidgetsBinding.instance.addPostFrameCallback((_) async {
        try {
          final n = await native.invokeMethod<String>('pendingCall');
          if (n != null) openCall(n, s);
        } catch (_) {}
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    if (!ready) return const SizedBox();
    return MaterialApp(
      navigatorKey: navKey,
      debugShowCheckedModeBanner: false,
      title: 'Fake Call',
      theme: ThemeData(
        useMaterial3: true,
        colorSchemeSeed: Color(s.accent),
        brightness: s.dark ? Brightness.dark : Brightness.light,
      ),
      home: Home(s: s),
    );
  }
}

class Home extends StatefulWidget {
  final Settings s;
  const Home({super.key, required this.s});
  @override
  State<Home> createState() => _HomeState();
}

class _HomeState extends State<Home> {
  Timer? tick;
  int left = 0;
  bool a11y = true;

  @override
  void initState() {
    super.initState();
    poll();
    tick = Timer.periodic(const Duration(seconds: 1), (_) => poll());
  }

  @override
  void dispose() {
    tick?.cancel();
    super.dispose();
  }

  Future<void> poll() async {
    try {
      final end = await native.invokeMethod<int>('status') ?? 0;
      final ok = await native.invokeMethod<bool>('a11yEnabled') ?? false;
      final l = end > 0 ? ((end - DateTime.now().millisecondsSinceEpoch) / 1000).ceil() : 0;
      if (mounted) setState(() { left = l < 0 ? 0 : l; a11y = ok; });
    } catch (_) {}
  }

  Future<void> arm() async { await native.invokeMethod('arm'); poll(); }
  Future<void> cancel() async { await native.invokeMethod('cancel'); poll(); }

  bool get armed => left > 0;
  String fmt(int sec) => sec >= 60 ? '${sec ~/ 60}m ${sec % 60}s' : '${sec}s';

  @override
  Widget build(BuildContext context) {
    final s = widget.s;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Fake Call'),
        actions: [
          IconButton(
            icon: const Icon(Icons.settings),
            onPressed: () => Navigator.of(context)
                .push(MaterialPageRoute(builder: (_) => SettingsPage(s: s))),
          ),
        ],
      ),
      body: Column(
        children: [
          if (s.volumeTrigger && !a11y)
            Card(
              margin: const EdgeInsets.all(12),
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Background volume trigger is off. Turn on "Fake Call" in Accessibility settings so Volume Up + Down works when the phone is locked.'),
                    Wrap(spacing: 8, children: [
                      TextButton(onPressed: () => native.invokeMethod('openA11y'), child: const Text('Accessibility')),
                      TextButton(onPressed: () => native.invokeMethod('openAppInfo'), child: const Text('Allow restricted settings')),
                    ]),
                  ],
                ),
              ),
            ),
          Expanded(
            child: Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(armed ? 'Ringing in ${fmt(left)}' : 'Caller: ${s.selected ?? 'Random'}  •  Delay: ${fmt(s.delay)}',
                      style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 24),
                  GestureDetector(
                    onTap: armed ? cancel : arm,
                    child: CircleAvatar(
                      radius: 70,
                      backgroundColor: armed ? Colors.red : Color(s.accent),
                      child: Icon(armed ? Icons.close : Icons.call, size: 56, color: Colors.white),
                    ),
                  ),
                  const SizedBox(height: 24),
                  Text(armed ? 'Tap to cancel' : 'Tap to start'),
                  if (s.volumeTrigger && !armed)
                    const Padding(
                      padding: EdgeInsets.all(16),
                      child: Text('Or press Volume Up + Down together', textAlign: TextAlign.center),
                    ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class CallPage extends StatefulWidget {
  final String name;
  final Settings s;
  const CallPage({super.key, required this.name, required this.s});
  @override
  State<CallPage> createState() => _CallPageState();
}

class _CallPageState extends State<CallPage> {
  bool answered = false, muted = false, weMuted = false, speaker = false;
  int secs = 0;
  Timer? clock;

  @override
  void initState() {
    super.initState();
    native.invokeMethod<bool>('getMute').then((v) {
      if (mounted) setState(() => muted = v ?? false);
    }).catchError((_) {});
  }

  void stopAlert() => native.invokeMethod('stopAlert');

  void toggleMute() {
    setState(() => muted = !muted);
    weMuted = muted;
    native.invokeMethod('setMute', muted);
  }

  void accept() {
    stopAlert();
    setState(() => answered = true);
    clock = Timer.periodic(const Duration(seconds: 1), (_) => setState(() => secs++));
  }

  void end() {
    clock?.cancel();
    Navigator.of(context).pop();
  }

  @override
  void dispose() {
    callOpen = false;
    clock?.cancel();
    native.invokeMethod('stopAlert');
    if (weMuted) {
      native.invokeMethod('setMute', false);
      weMuted = false;
    }
    native.invokeMethod('callEnded');
    super.dispose();
  }

  Widget circle(IconData i, Color c, VoidCallback f, {double r = 34}) => GestureDetector(
        onTap: f,
        child: CircleAvatar(radius: r, backgroundColor: c, child: Icon(i, color: Colors.white, size: r)),
      );

  @override
  Widget build(BuildContext context) {
    final mm = (secs ~/ 60).toString().padLeft(2, '0');
    final ss = (secs % 60).toString().padLeft(2, '0');
    return Scaffold(
      backgroundColor: const Color(0xFF111418),
      body: SafeArea(
        child: Column(
          children: [
            const SizedBox(height: 80),
            CircleAvatar(
              radius: 56,
              backgroundColor: Color(widget.s.accent),
              child: Text(widget.name[0].toUpperCase(),
                  style: const TextStyle(fontSize: 48, color: Colors.white)),
            ),
            const SizedBox(height: 24),
            Text(widget.name, style: const TextStyle(fontSize: 34, color: Colors.white)),
            const SizedBox(height: 8),
            Text(answered ? '$mm:$ss' : 'Incoming call • Mobile',
                style: const TextStyle(fontSize: 16, color: Colors.white70)),
            const Spacer(),
            Padding(
              padding: const EdgeInsets.only(bottom: 80),
              child: answered
                  ? Column(children: [
                      Row(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: [
                        Column(children: [
                          circle(muted ? Icons.volume_off : Icons.volume_up,
                              muted ? Color(widget.s.accent) : Colors.white24, toggleMute),
                          const SizedBox(height: 6),
                          Text(muted ? 'Unmute' : 'Mute',
                              style: const TextStyle(color: Colors.white70, fontSize: 12)),
                        ]),
                        circle(Icons.dialpad, Colors.white24, () {}),
                        circle(Icons.speaker_phone, speaker ? Color(widget.s.accent) : Colors.white24,
                            () => setState(() => speaker = !speaker)),
                      ]),
                      const SizedBox(height: 40),
                      circle(Icons.call_end, Colors.red, end, r: 36),
                    ])
                  : Row(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: [
                      circle(Icons.call_end, Colors.red, end, r: 36),
                      circle(Icons.call, Colors.green, accept, r: 36),
                    ]),
            ),
          ],
        ),
      ),
    );
  }
}

class SettingsPage extends StatefulWidget {
  final Settings s;
  const SettingsPage({super.key, required this.s});
  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  Settings get s => widget.s;
  static const delays = [5, 10, 30, 60, 300, 600];
  static const colors = [0xFF2E7D32, 0xFF1565C0, 0xFFC62828, 0xFF6A1B9A, 0xFFEF6C00, 0xFF00838F];

  String fmt(int sec) => sec >= 60 ? '${sec ~/ 60} min' : '$sec sec';

  Future<void> addName() async {
    final c = TextEditingController();
    final v = await showDialog<String>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Add caller name'),
        content: TextField(controller: c, autofocus: true),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
          TextButton(onPressed: () => Navigator.pop(context, c.text.trim()), child: const Text('Add')),
        ],
      ),
    );
    if (v != null && v.isNotEmpty) {
      s.names.add(v);
      s.save();
      setState(() {});
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const Text('Caller', style: TextStyle(fontWeight: FontWeight.bold)),
          RadioListTile<String?>(
            title: const Text('Random'),
            value: null,
            groupValue: s.selected,
            onChanged: (v) { s.selected = v; s.save(); setState(() {}); },
          ),
          for (final n in List<String>.from(s.names))
            RadioListTile<String?>(
              title: Text(n),
              value: n,
              groupValue: s.selected,
              secondary: IconButton(
                icon: const Icon(Icons.delete_outline),
                onPressed: s.names.length <= 1
                    ? null
                    : () {
                        s.names.remove(n);
                        if (s.selected == n) s.selected = null;
                        s.save();
                        setState(() {});
                      },
              ),
              onChanged: (v) { s.selected = v; s.save(); setState(() {}); },
            ),
          TextButton.icon(onPressed: addName, icon: const Icon(Icons.add), label: const Text('Add name')),
          const Divider(),
          const Text('Call after', style: TextStyle(fontWeight: FontWeight.bold)),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            children: [
              for (final d in delays)
                ChoiceChip(
                  label: Text(fmt(d)),
                  selected: s.delay == d,
                  onSelected: (_) { s.delay = d; s.save(); setState(() {}); },
                ),
            ],
          ),
          const Divider(),
          SwitchListTile(
            title: const Text('Volume Up + Down trigger'),
            subtitle: const Text('Works in background and on lock screen once Accessibility is enabled'),
            value: s.volumeTrigger,
            onChanged: (v) { s.volumeTrigger = v; s.save(); setState(() {}); },
          ),
          SwitchListTile(
            title: const Text('Screen-off volume trigger'),
            subtitle: const Text('Keeps a silent audio session and a small notification running so the trigger also works with the screen off. Uses some battery.'),
            value: s.screenOff,
            onChanged: (v) { s.screenOff = v; s.save(); setState(() {}); },
          ),
          ListTile(
            title: const Text('Open Accessibility settings'),
            subtitle: const Text('Turn on "Fake Call volume trigger"'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => native.invokeMethod('openA11y'),
          ),
          ListTile(
            title: const Text('Allow display over other apps'),
            subtitle: const Text('Lets the call open as a full screen when the phone is locked or the screen is off'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => native.invokeMethod('openOverlay'),
          ),
          ListTile(
            title: const Text('Allow restricted settings'),
            subtitle: const Text('Android 13+: App info → ⋮ menu → Allow restricted settings'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => native.invokeMethod('openAppInfo'),
          ),
          SwitchListTile(
            title: const Text('Ringtone'),
            value: s.ring,
            onChanged: (v) { s.ring = v; s.save(); setState(() {}); },
          ),
          SwitchListTile(
            title: const Text('Vibrate'),
            value: s.vibrate,
            onChanged: (v) { s.vibrate = v; s.save(); setState(() {}); },
          ),
          SwitchListTile(
            title: const Text('Dark theme'),
            value: s.dark,
            onChanged: (v) { s.dark = v; s.save(); setState(() {}); },
          ),
          const SizedBox(height: 8),
          const Text('Accent color', style: TextStyle(fontWeight: FontWeight.bold)),
          const SizedBox(height: 8),
          Wrap(
            spacing: 12,
            children: [
              for (final c in colors)
                GestureDetector(
                  onTap: () { s.accent = c; s.save(); setState(() {}); },
                  child: CircleAvatar(
                    backgroundColor: Color(c),
                    child: s.accent == c ? const Icon(Icons.check, color: Colors.white) : null,
                  ),
                ),
            ],
          ),
        ],
      ),
    );
  }
}
