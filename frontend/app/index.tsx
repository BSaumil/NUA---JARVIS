import React, { useState, useRef, useEffect, useCallback } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, FlatList, StyleSheet,
  KeyboardAvoidingView, Platform, Animated, ActivityIndicator, Keyboard, Alert,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import {
  useAudioRecorder, AudioModule, RecordingPresets,
  setAudioModeAsync, useAudioRecorderState, AudioPlayer,
} from 'expo-audio';
import * as ImagePicker from 'expo-image-picker';
import { useLocalSearchParams } from 'expo-router';

const API = process.env.EXPO_PUBLIC_BACKEND_URL;
const C = {
  bg: '#050505', surface: '#0F0F0F', surfEl: '#1A1A18',
  primary: '#FFB800', glow: 'rgba(255,184,0,0.4)', secondary: '#FF4500',
  accent: '#00FFCC', text: '#FFFFFF', textSec: '#A1A1AA', textTer: '#71717A',
  border: '#1E1E1E', hudBorder: 'rgba(255,184,0,0.15)', err: '#EF4444', ok: '#10B981',
};

interface Msg { id: string; role: 'user'|'assistant'; content: string; created_at: string; }

function TypingDots() {
  const dots = [useRef(new Animated.Value(0)).current, useRef(new Animated.Value(0)).current, useRef(new Animated.Value(0)).current];
  useEffect(() => {
    const anims = dots.map((d, i) => Animated.loop(Animated.sequence([
      Animated.delay(i * 200), Animated.timing(d, { toValue: 1, duration: 400, useNativeDriver: true }),
      Animated.timing(d, { toValue: 0, duration: 400, useNativeDriver: true }),
    ])));
    anims.forEach(a => a.start());
    return () => anims.forEach(a => a.stop());
  }, []);
  return (
    <View testID="typing-indicator" style={s.typingWrap}>
      <View style={s.aiBubble}>
        <View style={s.typingRow}>
          {dots.map((d, i) => (
            <Animated.View key={i} style={[s.dot, {
              opacity: d.interpolate({ inputRange: [0,1], outputRange: [0.3,1] }),
              transform: [{ scale: d.interpolate({ inputRange: [0,1], outputRange: [0.8,1.2] }) }],
            }]} />
          ))}
        </View>
      </View>
    </View>
  );
}

function Bubble({ msg, onPlay }: { msg: Msg; onPlay: (t: string) => void }) {
  const isU = msg.role === 'user';
  const fade = useRef(new Animated.Value(0)).current;
  const slide = useRef(new Animated.Value(10)).current;
  useEffect(() => {
    Animated.parallel([
      Animated.timing(fade, { toValue: 1, duration: 250, useNativeDriver: true }),
      Animated.timing(slide, { toValue: 0, duration: 250, useNativeDriver: true }),
    ]).start();
  }, []);
  return (
    <Animated.View testID={`bubble-${msg.id}`} style={[
      s.bubbleWrap, isU ? s.userWrap : s.aiWrap,
      { opacity: fade, transform: [{ translateY: slide }] },
    ]}>
      {!isU && (
        <View style={s.avatarBox}>
          <View style={s.avatar}><Text style={s.avatarText}>N</Text></View>
        </View>
      )}
      <View style={[isU ? s.userBubble : s.aiBubble, { flex: 1 }]}>  
        <Text style={s.bubbleText}>{msg.content}</Text>
        <View style={s.bubbleFoot}>
          <Text style={s.ts}>{new Date(msg.created_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</Text>
          {!isU && (
            <TouchableOpacity testID={`play-${msg.id}`} onPress={() => onPlay(msg.content)} style={s.audioBtn}>
              <Ionicons name="volume-high" size={14} color={C.primary} />
            </TouchableOpacity>
          )}
        </View>
      </View>
    </Animated.View>
  );
}

function Waveform({ active }: { active: boolean }) {
  const bars = useRef(Array.from({ length: 14 }, () => new Animated.Value(0.3))).current;
  useEffect(() => {
    if (!active) return;
    const anims = bars.map(b => Animated.loop(Animated.sequence([
      Animated.timing(b, { toValue: Math.random()*0.7+0.3, duration: 120+Math.random()*180, useNativeDriver: true }),
      Animated.timing(b, { toValue: Math.random()*0.3+0.1, duration: 120+Math.random()*180, useNativeDriver: true }),
    ])));
    anims.forEach(a => a.start());
    return () => anims.forEach(a => a.stop());
  }, [active]);
  if (!active) return null;
  return (
    <View testID="waveform" style={s.waveBox}>
      {bars.map((b, i) => <Animated.View key={i} style={[s.waveBar, { transform: [{ scaleY: b }] }]} />)}
    </View>
  );
}

export default function ChatScreen() {
  const params = useLocalSearchParams<{ conversationId?: string }>();
  const [msgs, setMsgs] = useState<Msg[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [recording, setRecording] = useState(false);
  const [speaking, setSpeaking] = useState(false);
  const [convId, setConvId] = useState<string|null>(null);
  const listRef = useRef<FlatList>(null);
  const playerRef = useRef<AudioPlayer|null>(null);
  const pulse = useRef(new Animated.Value(1)).current;
  const recorder = useAudioRecorder(RecordingPresets.HIGH_QUALITY);

  useEffect(() => { (async () => { await AudioModule.requestRecordingPermissionsAsync(); })(); }, []);
  useEffect(() => { if (params.conversationId) loadConv(params.conversationId); }, [params.conversationId]);
  useEffect(() => {
    if (recording) { Animated.loop(Animated.sequence([
      Animated.timing(pulse, { toValue: 1.3, duration: 500, useNativeDriver: true }),
      Animated.timing(pulse, { toValue: 1, duration: 500, useNativeDriver: true }),
    ])).start(); } else { pulse.setValue(1); }
  }, [recording]);

  const loadConv = async (id: string) => {
    try {
      const r = await fetch(`${API}/api/conversations/${id}`);
      const d = await r.json();
      if (d.messages) { setMsgs(d.messages); setConvId(id); }
    } catch (e) { console.error(e); }
  };

  const send = useCallback(async () => {
    const t = input.trim(); if (!t || loading) return;
    Keyboard.dismiss(); setInput(''); setLoading(true);
    setMsgs(p => [...p, { id: `t-${Date.now()}`, role: 'user', content: t, created_at: new Date().toISOString() }]);
    try {
      const r = await fetch(`${API}/api/chat`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ content: t, conversation_id: convId }) });
      const d = await r.json();
      if (d.error) { setMsgs(p => [...p, { id: `e-${Date.now()}`, role: 'assistant', content: `Hmm, hit a snag: ${d.error}`, created_at: new Date().toISOString() }]); }
      else { if (!convId) setConvId(d.conversation_id); setMsgs(p => [...p, d.message]); }
    } catch { setMsgs(p => [...p, { id: `e-${Date.now()}`, role: 'assistant', content: 'Connection hiccup. Give me a moment.', created_at: new Date().toISOString() }]); }
    finally { setLoading(false); }
  }, [input, loading, convId]);

  const startRec = async () => {
    try {
      await setAudioModeAsync({ allowsRecording: true, playsInSilentMode: true });
      await recorder.prepareToRecordAsync(); recorder.record(); setRecording(true);
    } catch (e) { console.error(e); }
  };

  const stopRec = async () => {
    if (!recording) return; setRecording(false); setLoading(true);
    try {
      await recorder.stop(); const uri = recorder.uri;
      if (!uri) { setLoading(false); return; }
      const fd = new FormData();
      fd.append('audio', { uri, type: 'audio/m4a', name: 'rec.m4a' } as any);
      if (convId) fd.append('conversation_id', convId);
      const r = await fetch(`${API}/api/chat/voice`, { method: 'POST', body: fd });
      const d = await r.json();
      if (d.error) { setMsgs(p => [...p, { id: `e-${Date.now()}`, role: 'assistant', content: d.error, created_at: new Date().toISOString() }]); }
      else {
        if (!convId) setConvId(d.conversation_id);
        setMsgs(p => [...p, { id: `v-${Date.now()}`, role: 'user', content: d.transcribed_text, created_at: new Date().toISOString() }, d.message]);
      }
    } catch (e) { console.error(e); } finally { setLoading(false); }
  };

  const pickImage = async () => {
    const perm = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!perm.granted) { Alert.alert('Permission needed', 'Allow photo access to use this feature.'); return; }
    const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], base64: true, quality: 0.5 });
    if (result.canceled || !result.assets[0].base64) return;
    setLoading(true);
    setMsgs(p => [...p, { id: `img-${Date.now()}`, role: 'user', content: '[Sent an image for analysis]', created_at: new Date().toISOString() }]);
    try {
      const fd = new FormData();
      fd.append('image', { uri: result.assets[0].uri, type: 'image/jpeg', name: 'photo.jpg' } as any);
      fd.append('content', input.trim() || 'Analyze this image');
      if (convId) fd.append('conversation_id', convId);
      setInput('');
      const r = await fetch(`${API}/api/chat/image`, { method: 'POST', body: fd });
      const d = await r.json();
      if (!convId && d.conversation_id) setConvId(d.conversation_id);
      if (d.message) setMsgs(p => [...p, d.message]);
    } catch (e) { console.error(e); } finally { setLoading(false); }
  };

  const playAudio = async (text: string) => {
    if (speaking) { playerRef.current?.pause(); playerRef.current?.release(); playerRef.current = null; setSpeaking(false); return; }
    setSpeaking(true);
    try {
      const r = await fetch(`${API}/api/tts`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ text, voice: 'nova' }) });
      const d = await r.json();
      if (d.audio_base64) {
        await setAudioModeAsync({ playsInSilentMode: true });
        const p = new AudioPlayer(`data:audio/mp3;base64,${d.audio_base64}`);
        playerRef.current = p; p.play();
        const iv = setInterval(() => { if (!p.playing) { clearInterval(iv); setSpeaking(false); p.release(); playerRef.current = null; } }, 500);
      }
    } catch { setSpeaking(false); }
  };

  const newChat = () => { setMsgs([]); setConvId(null); };

  return (
    <SafeAreaView style={s.container} edges={['top']}>
      <View testID="chat-header" style={s.header}>
        <View style={s.hLeft}>
          <View style={s.statusDot} />
          <View>
            <Text style={s.hTitle}>NUA</Text>
            <Text style={s.hSub}>{loading ? 'THINKING...' : speaking ? 'SPEAKING...' : recording ? 'LISTENING...' : 'READY'}</Text>
          </View>
        </View>
        <TouchableOpacity testID="new-chat-btn" onPress={newChat} style={s.hBtn}>
          <Ionicons name="add-circle-outline" size={24} color={C.primary} />
        </TouchableOpacity>
      </View>

      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : 'height'} style={s.chatArea} keyboardVerticalOffset={100}>
        <FlatList ref={listRef} testID="msg-list" data={msgs} renderItem={({ item }) => <Bubble msg={item} onPlay={playAudio} />}
          keyExtractor={i => i.id}
          contentContainerStyle={[s.msgList, msgs.length === 0 && s.emptyList]}
          ListEmptyComponent={
            <View style={s.emptyBox}>
              <View style={s.logo}><View style={s.logoInner}><Text style={s.logoText}>N</Text></View></View>
              <Text style={s.emptyTitle}>NUA</Text>
              <Text style={s.emptySub}>Your Intelligent Personal Assistant</Text>
              <Text style={s.emptyHint}>Type, speak, or send an image</Text>
              <View style={s.chips}>
                {['What can you do?', 'Remember my name is...', 'Remind me to...'].map((t, i) => (
                  <TouchableOpacity key={i} testID={`sug-${i}`} style={s.chip} onPress={() => setInput(t)}>
                    <Text style={s.chipText}>{t}</Text>
                  </TouchableOpacity>
                ))}
              </View>
            </View>
          }
          ListFooterComponent={loading ? <TypingDots /> : null}
          onContentSizeChange={() => { if (msgs.length > 0) listRef.current?.scrollToEnd({ animated: true }); }}
          showsVerticalScrollIndicator={false}
        />
        <Waveform active={recording} />
        <View testID="input-area" style={s.inputBox}>
          <View style={s.inputRow}>
            <TouchableOpacity testID="image-btn" onPress={pickImage} style={s.iconBtn}>
              <Ionicons name="image" size={20} color={C.primary} />
            </TouchableOpacity>
            <TextInput testID="msg-input" style={s.textInput} placeholder="Ask Nua anything..."
              placeholderTextColor={C.textTer} value={input} onChangeText={setInput}
              onSubmitEditing={send} returnKeyType="send" multiline maxLength={2000} />
            {input.trim() ? (
              <TouchableOpacity testID="send-btn" onPress={send} style={s.sendBtn} disabled={loading}>
                {loading ? <ActivityIndicator size="small" color={C.bg} /> : <Ionicons name="send" size={18} color={C.bg} />}
              </TouchableOpacity>
            ) : (
              <Animated.View style={{ transform: [{ scale: pulse }] }}>
                <TouchableOpacity testID="mic-btn" onPressIn={startRec} onPressOut={stopRec}
                  style={[s.micBtn, recording && s.micActive]}>
                  <Ionicons name={recording ? 'radio' : 'mic'} size={20} color={recording ? C.bg : C.primary} />
                </TouchableOpacity>
              </Animated.View>
            )}
          </View>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, backgroundColor: C.bg },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 20, paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: C.border, backgroundColor: C.surface },
  hLeft: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  statusDot: { width: 10, height: 10, borderRadius: 5, backgroundColor: C.ok },
  hTitle: { fontSize: 20, fontWeight: '800', color: C.primary, letterSpacing: 4 },
  hSub: { fontSize: 9, fontWeight: '600', color: C.textTer, letterSpacing: 2 },
  hBtn: { padding: 8 },
  chatArea: { flex: 1 },
  msgList: { paddingHorizontal: 16, paddingVertical: 12 },
  emptyList: { flex: 1, justifyContent: 'center' },
  bubbleWrap: { flexDirection: 'row', marginBottom: 14, maxWidth: '85%' },
  userWrap: { alignSelf: 'flex-end' },
  aiWrap: { alignSelf: 'flex-start' },
  userBubble: { backgroundColor: C.surfEl, borderWidth: 1, borderColor: C.border, borderRadius: 16, borderBottomRightRadius: 4, padding: 12 },
  aiBubble: { backgroundColor: 'rgba(255,184,0,0.04)', borderWidth: 1, borderColor: C.hudBorder, borderRadius: 16, borderBottomLeftRadius: 4, padding: 12 },
  avatarBox: { marginRight: 8, alignSelf: 'flex-end' },
  avatar: { width: 30, height: 30, borderRadius: 15, borderWidth: 1.5, borderColor: C.primary, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(255,184,0,0.1)' },
  avatarText: { color: C.primary, fontSize: 14, fontWeight: '800' },
  bubbleText: { fontSize: 14, lineHeight: 21, color: C.text },
  bubbleFoot: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 4 },
  ts: { fontSize: 9, color: C.textTer, letterSpacing: 0.5 },
  audioBtn: { padding: 4, marginLeft: 8 },
  typingWrap: { paddingHorizontal: 16, paddingBottom: 6, alignSelf: 'flex-start' },
  typingRow: { flexDirection: 'row', gap: 5, paddingVertical: 4 },
  dot: { width: 7, height: 7, borderRadius: 4, backgroundColor: C.primary },
  waveBox: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', height: 44, gap: 3, backgroundColor: 'rgba(255,184,0,0.04)', borderTopWidth: 1, borderTopColor: C.hudBorder },
  waveBar: { width: 3, height: 28, borderRadius: 2, backgroundColor: C.primary },
  inputBox: { paddingHorizontal: 12, paddingVertical: 10, borderTopWidth: 1, borderTopColor: C.border, backgroundColor: C.surface },
  inputRow: { flexDirection: 'row', alignItems: 'flex-end', gap: 8 },
  iconBtn: { width: 40, height: 40, borderRadius: 20, borderWidth: 1, borderColor: C.border, alignItems: 'center', justifyContent: 'center' },
  textInput: { flex: 1, backgroundColor: C.surfEl, borderWidth: 1, borderColor: C.border, borderRadius: 22, paddingHorizontal: 16, paddingVertical: 10, color: C.text, fontSize: 14, maxHeight: 100 },
  sendBtn: { width: 40, height: 40, borderRadius: 20, backgroundColor: C.primary, alignItems: 'center', justifyContent: 'center' },
  micBtn: { width: 40, height: 40, borderRadius: 20, borderWidth: 1.5, borderColor: C.primary, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(255,184,0,0.08)' },
  micActive: { backgroundColor: C.primary, borderColor: C.primary },
  emptyBox: { alignItems: 'center', paddingHorizontal: 32 },
  logo: { width: 88, height: 88, borderRadius: 44, borderWidth: 2, borderColor: C.primary, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(255,184,0,0.06)', marginBottom: 16 },
  logoInner: { width: 56, height: 56, borderRadius: 28, borderWidth: 1, borderColor: C.hudBorder, alignItems: 'center', justifyContent: 'center' },
  logoText: { fontSize: 28, fontWeight: '900', color: C.primary },
  emptyTitle: { fontSize: 32, fontWeight: '900', color: C.primary, letterSpacing: 6, marginBottom: 4 },
  emptySub: { fontSize: 11, color: C.textTer, letterSpacing: 1.5, marginBottom: 20, textTransform: 'uppercase' },
  emptyHint: { fontSize: 13, color: C.textSec, marginBottom: 20 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'center', gap: 8 },
  chip: { borderWidth: 1, borderColor: C.hudBorder, borderRadius: 20, paddingHorizontal: 14, paddingVertical: 9, backgroundColor: 'rgba(255,184,0,0.04)' },
  chipText: { fontSize: 12, color: C.primary, fontWeight: '500' },
});
