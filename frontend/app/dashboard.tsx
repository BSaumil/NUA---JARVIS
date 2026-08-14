import React, { useState, useCallback, useRef, useEffect } from 'react';
import { View, Text, TouchableOpacity, ScrollView, StyleSheet, ActivityIndicator, Animated, Linking } from 'react-native';
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

const WEATHER_ICONS: Record<string, string> = {
  'sunny': 'sunny', 'partly-sunny': 'partly-sunny', 'cloudy': 'cloud',
  'rainy': 'rainy', 'snow': 'snow', 'thunderstorm': 'thunderstorm',
};

export default function DashboardScreen() {
  const router = useRouter();
  const [dash, setDash] = useState<any>(null);
  const [weather, setWeather] = useState<any>(null);
  const [news, setNews] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [actionResp, setActionResp] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  const [trust, setTrust] = useState<any>(null);
  const [latestDream, setLatestDream] = useState<any>(null);

  useFocusEffect(useCallback(() => { loadAll(); }, []));

  const loadAll = async () => {
    setLoading(true);
    try {
      const [d, w, n, t, dr] = await Promise.all([
        fetch(`${API}/api/dashboard`).then(r => r.json()),
        fetch(`${API}/api/weather`).then(r => r.json()),
        fetch(`${API}/api/news`).then(r => r.json()),
        fetch(`${API}/api/trust`).then(r => r.json()),
        fetch(`${API}/api/dreams`).then(r => r.json()),
      ]);
      setDash(d); setWeather(w); setNews(n.articles || []);
      setTrust(t); setLatestDream(dr.dreams?.[0] || null);
    } catch (e) { console.error(e); } finally { setLoading(false); }
  };

  const quickAction = async (action: string) => {
    setActionLoading(action); setActionResp(null);
    try {
      const r = await fetch(`${API}/api/quick-action`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ action }) });
      const d = await r.json();
      setActionResp(d.response || 'Done!');
    } catch { setActionResp('Connection issue.'); } finally { setActionLoading(null); }
  };

  if (loading) return (
    <SafeAreaView style={st.container} edges={['top']}>
      <View style={st.center}><ActivityIndicator size="large" color={C.primary} /><Text style={st.loadText}>LOADING SYSTEMS...</Text></View>
    </SafeAreaView>
  );

  const s = dash?.stats;

  return (
    <SafeAreaView style={st.container} edges={['top']}>
      <View testID="dash-header" style={st.header}>
        <Text style={st.hTitle}>DASHBOARD</Text>
        <Text style={st.hSub}>NUA COMMAND CENTER</Text>
      </View>
      <ScrollView contentContainerStyle={st.scrollContent} showsVerticalScrollIndicator={false}>
        {/* Weather Widget */}
        {weather && !weather.error && (
          <View testID="weather-widget" style={st.weatherCard}>
            <View style={st.weatherRow}>
              <View>
                <Text style={st.weatherTemp}>{Math.round(weather.temperature)}°</Text>
                <Text style={st.weatherDesc}>{weather.description}</Text>
                <Text style={st.weatherMeta}>Feels {Math.round(weather.feels_like)}° • 💧{weather.humidity}% • 💨{weather.wind_speed}km/h</Text>
              </View>
              <Ionicons name={(WEATHER_ICONS[weather.icon] || 'cloud') as any} size={48} color={C.primary} />
            </View>
            {weather.forecast && weather.forecast.length > 0 && (
              <ScrollView horizontal showsHorizontalScrollIndicator={false} style={st.forecastRow}>
                {weather.forecast.slice(0, 5).map((f: any, i: number) => (
                  <View key={i} style={st.forecastDay}>
                    <Text style={st.forecastDate}>{f.date?.slice(5) || ''}</Text>
                    <Ionicons name={(WEATHER_ICONS[f.icon] || 'cloud') as any} size={18} color={C.textSec} />
                    <Text style={st.forecastTemp}>{Math.round(f.max)}°/{Math.round(f.min)}°</Text>
                  </View>
                ))}
              </ScrollView>
            )}
          </View>
        )}

        {/* Trust Score Widget */}
        {trust && (
          <View testID="trust-widget" style={st.trustCard}>
            <View style={st.trustRow}>
              <View>
                <Text style={st.trustScore}>{trust.score}%</Text>
                <Text style={st.trustLevel}>{trust.level}</Text>
              </View>
              <View style={st.trustMeter}>
                <View style={[st.trustFill, { width: `${trust.score}%` }]} />
              </View>
            </View>
            <Text style={st.trustLabel}>NUA TRUST SCORE • {trust.total_events || 0} events tracked</Text>
          </View>
        )}

        {/* Latest Dream */}
        {latestDream && (
          <View testID="dream-widget" style={[st.dreamWidget, { borderLeftColor: '#A855F7' }]}>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, marginBottom: 4 }}>
              <Ionicons name="sparkles" size={14} color="#A855F7" />
              <Text style={{ fontSize: 9, color: '#A855F7', fontWeight: '700', letterSpacing: 1 }}>LATEST NUA DREAM</Text>
            </View>
            <Text style={{ fontSize: 13, fontWeight: '600', color: C.text, marginBottom: 2 }}>{latestDream.title}</Text>
            <Text style={{ fontSize: 11, color: C.textSec, lineHeight: 16 }} numberOfLines={2}>{latestDream.content}</Text>
          </View>
        )}

        {/* Stats Row */}
        <View style={st.statsRow}>
          {[
            { icon: 'chatbubbles', val: s?.conversations || 0, label: 'Chats', color: C.primary },
            { icon: 'bulb', val: s?.memories || 0, label: 'Memory', color: C.accent },
            { icon: 'alarm', val: s?.active_reminders || 0, label: 'Reminders', color: C.warn },
            { icon: 'wallet', val: `$${(s?.total_expenses || 0).toFixed(0)}`, label: 'Spent', color: C.err },
          ].map((item, i) => (
            <View key={i} style={st.miniStat}>
              <Ionicons name={item.icon as any} size={18} color={item.color} />
              <Text style={st.miniVal}>{item.val}</Text>
              <Text style={st.miniLabel}>{item.label}</Text>
            </View>
          ))}
        </View>

        {/* Quick Access */}
        <View style={st.quickAccessRow}>
          <TouchableOpacity testID="nav-smarthome" style={st.quickAccessBtn} onPress={() => router.push('/smarthome')}>
            <Ionicons name="home" size={22} color="#06B6D4" />
            <Text style={st.qaLabel}>Smart Home</Text>
          </TouchableOpacity>
          <TouchableOpacity testID="nav-marketplace" style={st.quickAccessBtn} onPress={() => router.push('/marketplace')}>
            <Ionicons name="storefront" size={22} color="#A855F7" />
            <Text style={st.qaLabel}>Marketplace</Text>
          </TouchableOpacity>
          <TouchableOpacity testID="nav-history" style={st.quickAccessBtn} onPress={() => router.push('/history')}>
            <Ionicons name="time" size={22} color={C.primary} />
            <Text style={st.qaLabel}>History</Text>
          </TouchableOpacity>
        </View>

        {/* Proactive Suggestions */}
        {dash?.suggestions?.map((sug: any, i: number) => (
          <TouchableOpacity key={i} testID={`sug-${i}`} style={st.sugCard}>
            <Ionicons name="sparkles" size={14} color={C.primary} />
            <Text style={st.sugText}>{sug.text}</Text>
          </TouchableOpacity>
        ))}

        {/* Quick Actions */}
        <View style={st.section}>
          <Text style={st.secTitle}>QUICK ACTIONS</Text>
          {actionResp && (
            <View style={st.respBox}>
              <Text style={st.respText}>{actionResp}</Text>
              <TouchableOpacity onPress={() => setActionResp(null)}><Ionicons name="close-circle" size={16} color={C.textTer} /></TouchableOpacity>
            </View>
          )}
          <View style={st.actionsGrid}>
            {[
              { id: 'joke', icon: 'happy', label: 'Joke', color: C.primary },
              { id: 'fact', icon: 'flask', label: 'Fact', color: C.accent },
              { id: 'motivation', icon: 'rocket', label: 'Motivate', color: '#FF4500' },
              { id: 'trivia', icon: 'help-circle', label: 'Trivia', color: '#A855F7' },
              { id: 'wellness', icon: 'heart', label: 'Wellness', color: '#EC4899' },
              { id: 'code', icon: 'code-slash', label: 'Code', color: C.info },
            ].map(a => (
              <TouchableOpacity key={a.id} testID={`qa-${a.id}`} style={st.actionBtn} onPress={() => quickAction(a.id)}>
                {actionLoading === a.id ? <ActivityIndicator size="small" color={a.color} /> : <Ionicons name={a.icon as any} size={20} color={a.color} />}
                <Text style={st.actionLabel}>{a.label}</Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        {/* News Headlines */}
        {news.length > 0 && (
          <View style={st.section}>
            <Text style={st.secTitle}>NEWS HEADLINES</Text>
            {news.slice(0, 5).map((a, i) => (
              <TouchableOpacity key={i} testID={`news-${i}`} style={st.newsCard} onPress={() => a.link && Linking.openURL(a.link)}>
                <View style={st.newsDot} />
                <View style={{ flex: 1 }}>
                  <Text style={st.newsTitle} numberOfLines={2}>{a.title}</Text>
                  <Text style={st.newsMeta}>{a.source} • {a.published?.slice(0, 16) || ''}</Text>
                </View>
              </TouchableOpacity>
            ))}
          </View>
        )}

        {/* Reminders */}
        {dash?.upcoming_reminders?.length > 0 && (
          <View style={st.section}>
            <Text style={st.secTitle}>ACTIVE REMINDERS</Text>
            {dash.upcoming_reminders.map((r: any, i: number) => (
              <View key={i} style={st.remCard}>
                <View style={st.remDot} />
                <Text style={st.remTitle}>{r.title}</Text>
              </View>
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
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  loadText: { marginTop: 12, fontSize: 10, color: C.textTer, letterSpacing: 2 },
  header: { paddingHorizontal: 20, paddingVertical: 14, borderBottomWidth: 1, borderBottomColor: C.border, backgroundColor: C.surface },
  hTitle: { fontSize: 20, fontWeight: '800', color: C.primary, letterSpacing: 3 },
  hSub: { fontSize: 9, color: C.textTer, letterSpacing: 1.5, marginTop: 2 },
  scrollContent: { padding: 16 },
  weatherCard: { backgroundColor: C.surfEl, borderWidth: 1, borderColor: C.hudBorder, borderRadius: 14, padding: 16, marginBottom: 14 },
  weatherRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  weatherTemp: { fontSize: 42, fontWeight: '900', color: C.text },
  weatherDesc: { fontSize: 14, color: C.primary, fontWeight: '600' },
  weatherMeta: { fontSize: 11, color: C.textTer, marginTop: 4 },
  forecastRow: { marginTop: 12, borderTopWidth: 1, borderTopColor: C.border, paddingTop: 12 },
  forecastDay: { alignItems: 'center', marginRight: 20, gap: 4 },
  forecastDate: { fontSize: 10, color: C.textTer },
  forecastTemp: { fontSize: 10, color: C.textSec },
  statsRow: { flexDirection: 'row', gap: 8, marginBottom: 14 },
  miniStat: { flex: 1, backgroundColor: C.surfEl, borderWidth: 1, borderColor: C.border, borderRadius: 10, padding: 10, alignItems: 'center', gap: 4 },
  miniVal: { fontSize: 16, fontWeight: '800', color: C.text },
  miniLabel: { fontSize: 8, color: C.textTer, letterSpacing: 1, textTransform: 'uppercase' },
  quickAccessRow: { flexDirection: 'row', gap: 8, marginBottom: 14 },
  quickAccessBtn: { flex: 1, backgroundColor: C.surfEl, borderWidth: 1, borderColor: C.border, borderRadius: 10, padding: 14, alignItems: 'center', gap: 6 },
  qaLabel: { fontSize: 10, color: C.textSec, fontWeight: '600' },
  sugCard: { flexDirection: 'row', alignItems: 'center', gap: 10, backgroundColor: 'rgba(255,184,0,0.03)', borderWidth: 1, borderColor: C.hudBorder, borderRadius: 10, padding: 12, marginBottom: 8 },
  sugText: { fontSize: 12, color: C.textSec, flex: 1 },
  section: { marginBottom: 16 },
  secTitle: { fontSize: 10, fontWeight: '700', color: C.textTer, letterSpacing: 2, marginBottom: 10 },
  respBox: { flexDirection: 'row', gap: 10, backgroundColor: C.surfEl, borderWidth: 1, borderColor: C.hudBorder, borderRadius: 10, padding: 12, marginBottom: 10 },
  respText: { fontSize: 13, lineHeight: 20, color: C.text, flex: 1 },
  actionsGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  actionBtn: { width: '31%', backgroundColor: C.surfEl, borderWidth: 1, borderColor: C.border, borderRadius: 10, padding: 12, alignItems: 'center', gap: 4, minWidth: 95 },
  actionLabel: { fontSize: 9, color: C.textSec, fontWeight: '600' },
  newsCard: { flexDirection: 'row', alignItems: 'flex-start', gap: 10, backgroundColor: C.surfEl, borderWidth: 1, borderColor: C.border, borderRadius: 10, padding: 12, marginBottom: 6 },
  newsDot: { width: 6, height: 6, borderRadius: 3, backgroundColor: C.info, marginTop: 6 },
  newsTitle: { fontSize: 13, color: C.text, fontWeight: '500', lineHeight: 18 },
  newsMeta: { fontSize: 9, color: C.textTer, marginTop: 4 },
  remCard: { flexDirection: 'row', alignItems: 'center', gap: 10, backgroundColor: C.surfEl, borderRadius: 8, padding: 10, marginBottom: 4 },
  remDot: { width: 8, height: 8, borderRadius: 4, backgroundColor: C.warn },
  remTitle: { fontSize: 12, color: C.text },
  trustCard: { backgroundColor: C.surfEl, borderWidth: 1, borderColor: C.hudBorder, borderRadius: 14, padding: 14, marginBottom: 14 },
  trustRow: { flexDirection: 'row', alignItems: 'center', gap: 14 },
  trustScore: { fontSize: 32, fontWeight: '900', color: C.accent },
  trustLevel: { fontSize: 11, color: C.primary, fontWeight: '600' },
  trustMeter: { flex: 1, height: 8, backgroundColor: C.border, borderRadius: 4 },
  trustFill: { height: 8, backgroundColor: C.accent, borderRadius: 4 },
  trustLabel: { fontSize: 8, color: C.textTer, letterSpacing: 1.5, marginTop: 8, textTransform: 'uppercase' },
  dreamWidget: { backgroundColor: C.surfEl, borderWidth: 1, borderColor: C.border, borderLeftWidth: 3, borderRadius: 10, padding: 12, marginBottom: 14 },
});
