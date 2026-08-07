import React, { useState, useEffect } from 'react';
import { View, Text, TouchableOpacity, ScrollView, StyleSheet, ActivityIndicator } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';

const API = process.env.EXPO_PUBLIC_BACKEND_URL;
const C = {
  bg: '#050505', surface: '#0F0F0F', surfEl: '#1A1A18',
  primary: '#FFB800', accent: '#00FFCC',
  text: '#FFFFFF', textSec: '#A1A1AA', textTer: '#71717A',
  border: '#1E1E1E', hudBorder: 'rgba(255,184,0,0.15)',
  ok: '#10B981', warn: '#F59E0B', err: '#EF4444', info: '#3B82F6',
};

const CAT_COLORS: Record<string, string> = {
  Utility: C.primary, Information: C.info, IoT: '#06B6D4',
  Productivity: C.accent, Health: C.ok, Lifestyle: '#EC4899', Finance: C.warn,
};

export default function MarketplaceScreen() {
  const router = useRouter();
  const [skills, setSkills] = useState<any[]>([]);
  const [categories, setCategories] = useState<string[]>([]);
  const [selectedCat, setSelectedCat] = useState<string>('All');
  const [loading, setLoading] = useState(true);
  const [installing, setInstalling] = useState<string | null>(null);

  useEffect(() => { load(); }, []);

  const load = async () => {
    try {
      const r = await fetch(`${API}/api/skills/marketplace`);
      const d = await r.json();
      setSkills(d.skills || []);
      setCategories(['All', ...(d.categories || [])]);
    } catch (e) { console.error(e); } finally { setLoading(false); }
  };

  const toggleInstall = async (skill: any) => {
    setInstalling(skill.id);
    try {
      if (skill.installed) {
        await fetch(`${API}/api/skills/install/${skill.id}`, { method: 'DELETE' });
      } else {
        await fetch(`${API}/api/skills/install/${skill.id}`, { method: 'POST' });
      }
      load();
    } catch (e) { console.error(e); } finally { setInstalling(null); }
  };

  const filtered = selectedCat === 'All' ? skills : skills.filter(s => s.category === selectedCat);

  return (
    <SafeAreaView style={st.container} edges={['top']}>
      <View testID="mp-header" style={st.header}>
        <TouchableOpacity testID="mp-back" onPress={() => router.back()} style={st.backBtn}>
          <Ionicons name="chevron-back" size={22} color={C.primary} />
        </TouchableOpacity>
        <View>
          <Text style={st.hTitle}>SKILL MARKET</Text>
          <Text style={st.hSub}>EXPAND NUA'S CAPABILITIES</Text>
        </View>
      </View>

      {/* Category Filter */}
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={st.filterRow} contentContainerStyle={st.filterContent}>
        {categories.map(cat => (
          <TouchableOpacity key={cat} testID={`cat-${cat}`}
            style={[st.filterChip, selectedCat === cat && st.filterActive]}
            onPress={() => setSelectedCat(cat)}>
            <Text style={[st.filterText, selectedCat === cat && st.filterTextActive]}>{cat}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>

      {loading ? (
        <View style={st.center}><ActivityIndicator size="large" color={C.primary} /></View>
      ) : (
        <ScrollView contentContainerStyle={st.scrollContent} showsVerticalScrollIndicator={false}>
          {filtered.map(skill => (
            <View key={skill.id} testID={`skill-${skill.id}`} style={st.skillCard}>
              <View style={[st.skillIcon, { borderColor: (CAT_COLORS[skill.category] || C.textTer) + '30' }]}>
                <Ionicons name={(skill.icon || 'extension-puzzle') as any} size={24} color={CAT_COLORS[skill.category] || C.textTer} />
              </View>
              <View style={st.skillInfo}>
                <View style={st.skillHeader}>
                  <Text style={st.skillName}>{skill.name}</Text>
                  {skill.installed && <View style={st.installedBadge}><Text style={st.installedText}>INSTALLED</Text></View>}
                </View>
                <Text style={st.skillDesc}>{skill.description}</Text>
                <View style={st.skillMeta}>
                  <View style={st.ratingRow}>
                    <Ionicons name="star" size={12} color={C.primary} />
                    <Text style={st.ratingText}>{skill.rating}</Text>
                  </View>
                  <Text style={st.downloads}>{skill.downloads} downloads</Text>
                  <Text style={st.catBadge}>{skill.category}</Text>
                </View>
              </View>
              <TouchableOpacity testID={`install-${skill.id}`}
                style={[st.installBtn, skill.installed && st.uninstallBtn]}
                onPress={() => toggleInstall(skill)}>
                {installing === skill.id ? (
                  <ActivityIndicator size="small" color={skill.installed ? C.err : C.primary} />
                ) : (
                  <Text style={[st.installText, skill.installed && st.uninstallText]}>
                    {skill.installed ? 'Remove' : skill.free ? 'Install' : skill.price}
                  </Text>
                )}
              </TouchableOpacity>
            </View>
          ))}
          <View style={{ height: 32 }} />
        </ScrollView>
      )}
    </SafeAreaView>
  );
}

const st = StyleSheet.create({
  container: { flex: 1, backgroundColor: C.bg },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  header: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 16, paddingVertical: 14, borderBottomWidth: 1, borderBottomColor: C.border, backgroundColor: C.surface },
  backBtn: { padding: 4 },
  hTitle: { fontSize: 18, fontWeight: '800', color: C.primary, letterSpacing: 3 },
  hSub: { fontSize: 8, color: C.textTer, letterSpacing: 1.5, marginTop: 1 },
  filterRow: { maxHeight: 56, borderBottomWidth: 1, borderBottomColor: C.border, backgroundColor: C.surface },
  filterContent: { paddingHorizontal: 16, paddingVertical: 10, gap: 8 },
  filterChip: { paddingHorizontal: 14, paddingVertical: 7, borderRadius: 16, borderWidth: 1, borderColor: C.border, flexShrink: 0 },
  filterActive: { borderColor: C.primary, backgroundColor: 'rgba(255,184,0,0.1)' },
  filterText: { fontSize: 12, color: C.textTer, fontWeight: '600' },
  filterTextActive: { color: C.primary },
  scrollContent: { padding: 16 },
  skillCard: { flexDirection: 'row', alignItems: 'center', gap: 12, backgroundColor: C.surfEl, borderWidth: 1, borderColor: C.border, borderRadius: 12, padding: 14, marginBottom: 10 },
  skillIcon: { width: 48, height: 48, borderRadius: 12, borderWidth: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(255,255,255,0.02)' },
  skillInfo: { flex: 1 },
  skillHeader: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  skillName: { fontSize: 14, fontWeight: '700', color: C.text },
  installedBadge: { backgroundColor: 'rgba(16,185,129,0.15)', borderRadius: 3, paddingHorizontal: 5, paddingVertical: 1 },
  installedText: { fontSize: 7, color: C.ok, fontWeight: '700', letterSpacing: 0.5 },
  skillDesc: { fontSize: 11, color: C.textSec, marginTop: 2, lineHeight: 15 },
  skillMeta: { flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 4 },
  ratingRow: { flexDirection: 'row', alignItems: 'center', gap: 2 },
  ratingText: { fontSize: 10, color: C.primary, fontWeight: '600' },
  downloads: { fontSize: 9, color: C.textTer },
  catBadge: { fontSize: 8, color: C.textTer, backgroundColor: 'rgba(255,255,255,0.04)', borderRadius: 3, paddingHorizontal: 4, paddingVertical: 1 },
  installBtn: { borderWidth: 1, borderColor: C.primary, borderRadius: 8, paddingHorizontal: 14, paddingVertical: 8 },
  uninstallBtn: { borderColor: C.err },
  installText: { fontSize: 11, color: C.primary, fontWeight: '700' },
  uninstallText: { color: C.err },
});
