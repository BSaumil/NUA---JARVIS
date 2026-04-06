import React, { useState, useCallback, useRef, useEffect } from 'react';
import { View, Text, TouchableOpacity, ScrollView, StyleSheet, ActivityIndicator, Animated } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useFocusEffect } from 'expo-router';

const API = process.env.EXPO_PUBLIC_BACKEND_URL;
const C = {
  bg: '#050505', surface: '#0F0F0F', surfEl: '#1A1A18',
  primary: '#FFB800', glow: 'rgba(255,184,0,0.4)', accent: '#00FFCC',
  text: '#FFFFFF', textSec: '#A1A1AA', textTer: '#71717A',
  border: '#1E1E1E', hudBorder: 'rgba(255,184,0,0.15)',
  ok: '#10B981', warn: '#F59E0B', err: '#EF4444', info: '#3B82F6',
};

interface DashData {
  stats: { conversations: number; memories: number; active_reminders: number; notes: number; total_expenses: number };
  recent_conversations: any[];
  upcoming_reminders: any[];
  suggestions: any[];
}

function StatCard({ icon, label, value, color, index }: { icon: string; label: string; value: string; color: string; index: number }) {
  const fade = useRef(new Animated.Value(0)).current;
  useEffect(() => {
    Animated.timing(fade, { toValue: 1, duration: 400, delay: index * 100, useNativeDriver: true }).start();
  }, []);
  return (
    <Animated.View style={[st.statCard, { opacity: fade }]}>
      <View style={[st.statIcon, { borderColor: color + '30' }]}>
        <Ionicons name={icon as any} size={20} color={color} />
      </View>
      <Text style={st.statValue}>{value}</Text>
      <Text style={st.statLabel}>{label}</Text>
    </Animated.View>
  );
}

export default function DashboardScreen() {
  const router = useRouter();
  const [data, setData] = useState<DashData | null>(null);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [actionResponse, setActionResponse] = useState<string | null>(null);

  useFocusEffect(useCallback(() => { loadDash(); }, []));

  const loadDash = async () => {
    try {
      const r = await fetch(`${API}/api/dashboard`);
      const d = await r.json();
      setData(d);
    } catch (e) { console.error(e); } finally { setLoading(false); }
  };

  const executeAction = async (action: string) => {
    setActionLoading(action); setActionResponse(null);
    try {
      const r = await fetch(`${API}/api/quick-action`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ action }) });
      const d = await r.json();
      setActionResponse(d.response || 'Done!');
    } catch { setActionResponse('Connection issue.'); } finally { setActionLoading(null); }
  };

  if (loading) return (
    <SafeAreaView style={st.container} edges={['top']}>
      <View style={st.loadBox}><ActivityIndicator size="large" color={C.primary} /><Text style={st.loadText}>INITIALIZING...</Text></View>
    </SafeAreaView>
  );

  const s = data?.stats;

  return (
    <SafeAreaView style={st.container} edges={['top']}>
      <View testID="dash-header" style={st.header}>
        <Text style={st.hTitle}>DASHBOARD</Text>
        <Text style={st.hSub}>NUA COMMAND CENTER</Text>
      </View>
      <ScrollView style={st.scroll} contentContainerStyle={st.scrollContent} showsVerticalScrollIndicator={false}>
        {/* Stats Grid */}
        <View style={st.statsGrid}>
          <StatCard icon="chatbubbles" label="Chats" value={String(s?.conversations || 0)} color={C.primary} index={0} />
          <StatCard icon="brain" label="Memories" value={String(s?.memories || 0)} color={C.accent} index={1} />
          <StatCard icon="alarm" label="Reminders" value={String(s?.active_reminders || 0)} color={C.warn} index={2} />
          <StatCard icon="wallet" label="Expenses" value={`$${(s?.total_expenses || 0).toFixed(0)}`} color={C.err} index={3} />
        </View>

        {/* Proactive Suggestions */}
        {data?.suggestions && data.suggestions.length > 0 && (
          <View style={st.section}>
            <View style={st.secHeader}>
              <Ionicons name="bulb" size={16} color={C.primary} />
              <Text style={st.secTitle}>SUGGESTIONS</Text>
            </View>
            {data.suggestions.map((sug, i) => (
              <TouchableOpacity key={i} testID={`sug-${i}`} style={st.sugCard}
                onPress={() => { if (sug.query) router.push({ pathname: '/', params: { conversationId: '' } }); }}>
                <Ionicons name="sparkles" size={14} color={C.primary} />
                <Text style={st.sugText}>{sug.text}</Text>
              </TouchableOpacity>
            ))}
          </View>
        )}

        {/* Quick Actions */}
        <View style={st.section}>
          <View style={st.secHeader}>
            <Ionicons name="flash" size={16} color={C.primary} />
            <Text style={st.secTitle}>QUICK ACTIONS</Text>
          </View>
          {actionResponse && (
            <View style={st.responseBox}>
              <Text style={st.responseText}>{actionResponse}</Text>
              <TouchableOpacity onPress={() => setActionResponse(null)}>
                <Ionicons name="close-circle" size={18} color={C.textTer} />
              </TouchableOpacity>
            </View>
          )}
          <View style={st.actionsGrid}>
            {[
              { id: 'joke', icon: 'happy', label: 'Joke', color: C.primary },
              { id: 'fact', icon: 'flask', label: 'Fun Fact', color: C.accent },
              { id: 'motivation', icon: 'rocket', label: 'Motivate', color: '#FF4500' },
              { id: 'trivia', icon: 'help-circle', label: 'Trivia', color: '#A855F7' },
              { id: 'wellness', icon: 'heart', label: 'Wellness', color: '#EC4899' },
              { id: 'code', icon: 'code-slash', label: 'Code', color: C.info },
            ].map(a => (
              <TouchableOpacity key={a.id} testID={`qa-${a.id}`} style={st.actionBtn} onPress={() => executeAction(a.id)}>
                {actionLoading === a.id ? <ActivityIndicator size="small" color={a.color} /> : <Ionicons name={a.icon as any} size={22} color={a.color} />}
                <Text style={st.actionLabel}>{a.label}</Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        {/* Upcoming Reminders */}
        {data?.upcoming_reminders && data.upcoming_reminders.length > 0 && (
          <View style={st.section}>
            <View style={st.secHeader}>
              <Ionicons name="alarm" size={16} color={C.warn} />
              <Text style={st.secTitle}>ACTIVE REMINDERS</Text>
            </View>
            {data.upcoming_reminders.map((r: any, i: number) => (
              <View key={i} style={st.remCard}>
                <View style={st.remDot} />
                <View style={{ flex: 1 }}>
                  <Text style={st.remTitle}>{r.title}</Text>
                  {r.due_date ? <Text style={st.remDate}>{r.due_date}</Text> : null}
                </View>
              </View>
            ))}
          </View>
        )}

        {/* Recent Conversations */}
        {data?.recent_conversations && data.recent_conversations.length > 0 && (
          <View style={st.section}>
            <View style={st.secHeader}>
              <Ionicons name="time" size={16} color={C.textSec} />
              <Text style={st.secTitle}>RECENT CONVERSATIONS</Text>
            </View>
            {data.recent_conversations.map((conv: any, i: number) => (
              <TouchableOpacity key={i} testID={`recent-${i}`} style={st.convCard}
                onPress={() => router.push({ pathname: '/', params: { conversationId: conv.id } })}>
                <View style={st.convIcon}><Ionicons name="chatbubble" size={14} color={C.primary} /></View>
                <View style={{ flex: 1 }}>
                  <Text style={st.convTitle} numberOfLines={1}>{conv.title}</Text>
                  <Text style={st.convMeta}>{conv.message_count} messages</Text>
                </View>
                <Ionicons name="chevron-forward" size={16} color={C.textTer} />
              </TouchableOpacity>
            ))}
          </View>
        )}
        <View style={{ height: 32 }} />
      </ScrollView>
    </SafeAreaView>
  );
}

const st = StyleSheet.create({
  container: { flex: 1, backgroundColor: C.bg },
  loadBox: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  loadText: { marginTop: 12, fontSize: 10, color: C.textTer, letterSpacing: 2 },
  header: { paddingHorizontal: 20, paddingVertical: 14, borderBottomWidth: 1, borderBottomColor: C.border, backgroundColor: C.surface },
  hTitle: { fontSize: 20, fontWeight: '800', color: C.primary, letterSpacing: 3 },
  hSub: { fontSize: 9, color: C.textTer, letterSpacing: 1.5, marginTop: 2 },
  scroll: { flex: 1 },
  scrollContent: { padding: 16 },
  statsGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginBottom: 20 },
  statCard: { width: '48%', backgroundColor: C.surfEl, borderWidth: 1, borderColor: C.border, borderRadius: 12, padding: 14, minWidth: 150 },
  statIcon: { width: 36, height: 36, borderRadius: 10, borderWidth: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(255,255,255,0.02)', marginBottom: 10 },
  statValue: { fontSize: 22, fontWeight: '800', color: C.text, marginBottom: 2 },
  statLabel: { fontSize: 10, color: C.textTer, letterSpacing: 1, textTransform: 'uppercase' },
  section: { marginBottom: 20 },
  secHeader: { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 10 },
  secTitle: { fontSize: 11, fontWeight: '700', color: C.textTer, letterSpacing: 2 },
  sugCard: { flexDirection: 'row', alignItems: 'center', gap: 10, backgroundColor: 'rgba(255,184,0,0.04)', borderWidth: 1, borderColor: C.hudBorder, borderRadius: 10, padding: 12, marginBottom: 8 },
  sugText: { fontSize: 13, color: C.textSec, flex: 1 },
  responseBox: { flexDirection: 'row', alignItems: 'flex-start', gap: 10, backgroundColor: C.surfEl, borderWidth: 1, borderColor: C.hudBorder, borderRadius: 10, padding: 12, marginBottom: 10 },
  responseText: { fontSize: 13, lineHeight: 20, color: C.text, flex: 1 },
  actionsGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  actionBtn: { width: '31%', backgroundColor: C.surfEl, borderWidth: 1, borderColor: C.border, borderRadius: 10, padding: 14, alignItems: 'center', gap: 6, minWidth: 95 },
  actionLabel: { fontSize: 10, color: C.textSec, fontWeight: '600' },
  remCard: { flexDirection: 'row', alignItems: 'center', gap: 10, backgroundColor: C.surfEl, borderWidth: 1, borderColor: C.border, borderRadius: 10, padding: 12, marginBottom: 6 },
  remDot: { width: 8, height: 8, borderRadius: 4, backgroundColor: C.warn },
  remTitle: { fontSize: 13, color: C.text, fontWeight: '500' },
  remDate: { fontSize: 10, color: C.textTer, marginTop: 2 },
  convCard: { flexDirection: 'row', alignItems: 'center', gap: 10, backgroundColor: C.surfEl, borderWidth: 1, borderColor: C.border, borderRadius: 10, padding: 12, marginBottom: 6 },
  convIcon: { width: 32, height: 32, borderRadius: 16, borderWidth: 1, borderColor: C.hudBorder, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(255,184,0,0.06)' },
  convTitle: { fontSize: 13, color: C.text, fontWeight: '500' },
  convMeta: { fontSize: 10, color: C.textTer, marginTop: 2 },
});
