import React, { useState, useCallback } from 'react';
import { View, Text, TouchableOpacity, FlatList, StyleSheet, TextInput, Alert, ActivityIndicator } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from 'expo-router';

const API = process.env.EXPO_PUBLIC_BACKEND_URL;
const C = {
  bg: '#050505', surface: '#0F0F0F', surfEl: '#1A1A18',
  primary: '#FFB800', accent: '#00FFCC',
  text: '#FFFFFF', textSec: '#A1A1AA', textTer: '#71717A',
  border: '#1E1E1E', hudBorder: 'rgba(255,184,0,0.15)', err: '#EF4444',
};

interface Memory { id: string; category: string; key: string; value: string; source: string; created_at: string; }

const CAT_COLORS: Record<string, string> = {
  preference: '#FFB800', habit: '#00FFCC', personal: '#3B82F6', work: '#A855F7', general: '#71717A',
};

export default function MemoryScreen() {
  const [memories, setMemories] = useState<Memory[]>([]);
  const [loading, setLoading] = useState(true);
  const [showAdd, setShowAdd] = useState(false);
  const [newKey, setNewKey] = useState('');
  const [newVal, setNewVal] = useState('');
  const [newCat, setNewCat] = useState('personal');

  useFocusEffect(useCallback(() => { load(); }, []));

  const load = async () => {
    try {
      const r = await fetch(`${API}/api/memory`);
      const d = await r.json();
      setMemories(d.memories || []);
    } catch (e) { console.error(e); } finally { setLoading(false); }
  };

  const addMemory = async () => {
    if (!newKey.trim() || !newVal.trim()) return;
    try {
      await fetch(`${API}/api/memory`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ key: newKey, value: newVal, category: newCat }),
      });
      setNewKey(''); setNewVal(''); setShowAdd(false); load();
    } catch (e) { console.error(e); }
  };

  const deleteMemory = (id: string, key: string) => {
    Alert.alert('Forget this?', `Remove "${key}" from memory?`, [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Forget', style: 'destructive', onPress: async () => {
        await fetch(`${API}/api/memory/${id}`, { method: 'DELETE' }); load();
      }},
    ]);
  };

  const clearAll = () => {
    Alert.alert('Clear All Memory', 'This will erase everything Nua knows about you. Continue?', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Clear All', style: 'destructive', onPress: async () => {
        await fetch(`${API}/api/memory`, { method: 'DELETE' }); load();
      }},
    ]);
  };

  const grouped = memories.reduce<Record<string, Memory[]>>((acc, m) => {
    const cat = m.category || 'general';
    if (!acc[cat]) acc[cat] = [];
    acc[cat].push(m);
    return acc;
  }, {});

  return (
    <SafeAreaView style={st.container} edges={['top']}>
      <View testID="memory-header" style={st.header}>
        <View>
          <Text style={st.hTitle}>MEMORY</Text>
          <Text style={st.hSub}>WHAT NUA KNOWS ABOUT YOU</Text>
        </View>
        <View style={{ flexDirection: 'row', gap: 8 }}>
          <TouchableOpacity testID="add-memory-btn" onPress={() => setShowAdd(!showAdd)} style={st.hBtn}>
            <Ionicons name={showAdd ? 'close' : 'add'} size={20} color={C.primary} />
          </TouchableOpacity>
          {memories.length > 0 && (
            <TouchableOpacity testID="clear-memory-btn" onPress={clearAll} style={[st.hBtn, { borderColor: C.err + '30' }]}>
              <Ionicons name="trash-outline" size={16} color={C.err} />
            </TouchableOpacity>
          )}
        </View>
      </View>

      {showAdd && (
        <View testID="add-memory-form" style={st.addBox}>
          <View style={st.catRow}>
            {['personal', 'preference', 'habit', 'work'].map(cat => (
              <TouchableOpacity key={cat} style={[st.catBtn, newCat === cat && { backgroundColor: CAT_COLORS[cat] + '20', borderColor: CAT_COLORS[cat] }]}
                onPress={() => setNewCat(cat)}>
                <Text style={[st.catBtnText, newCat === cat && { color: CAT_COLORS[cat] }]}>{cat}</Text>
              </TouchableOpacity>
            ))}
          </View>
          <TextInput testID="memory-key-input" style={st.input} placeholder="What (e.g., Favorite food)" placeholderTextColor={C.textTer} value={newKey} onChangeText={setNewKey} />
          <TextInput testID="memory-val-input" style={st.input} placeholder="Detail (e.g., Italian pasta)" placeholderTextColor={C.textTer} value={newVal} onChangeText={setNewVal} />
          <TouchableOpacity testID="save-memory-btn" style={st.saveBtn} onPress={addMemory}>
            <Text style={st.saveBtnText}>Save to Memory</Text>
          </TouchableOpacity>
        </View>
      )}

      {loading ? (
        <View style={st.loadBox}><ActivityIndicator size="large" color={C.primary} /></View>
      ) : memories.length === 0 ? (
        <View style={st.emptyBox}>
          <Ionicons name="bulb" size={48} color={C.textTer} />
          <Text style={st.emptyTitle}>No Memories Yet</Text>
          <Text style={st.emptySub}>Chat with Nua or add manually. Say things like:{'\n'}"My name is..." or "I prefer..."</Text>
        </View>
      ) : (
        <FlatList testID="memory-list" data={Object.entries(grouped)}
          keyExtractor={([cat]) => cat}
          contentContainerStyle={st.listContent}
          renderItem={({ item: [cat, mems] }) => (
            <View style={st.catSection}>
              <View style={st.catHeader}>
                <View style={[st.catDot, { backgroundColor: CAT_COLORS[cat] || C.textTer }]} />
                <Text style={[st.catLabel, { color: CAT_COLORS[cat] || C.textTer }]}>{cat.toUpperCase()}</Text>
                <Text style={st.catCount}>{mems.length}</Text>
              </View>
              {mems.map(m => (
                <TouchableOpacity key={m.id} testID={`mem-${m.id}`} style={st.memCard}
                  onLongPress={() => deleteMemory(m.id, m.key)}>
                  <View style={{ flex: 1 }}>
                    <Text style={st.memKey}>{m.key}</Text>
                    <Text style={st.memVal}>{m.value}</Text>
                    <Text style={st.memSource}>{m.source === 'conversation' ? 'Learned from chat' : 'Added manually'}</Text>
                  </View>
                </TouchableOpacity>
              ))}
            </View>
          )}
          showsVerticalScrollIndicator={false}
        />
      )}
    </SafeAreaView>
  );
}

const st = StyleSheet.create({
  container: { flex: 1, backgroundColor: C.bg },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 20, paddingVertical: 14, borderBottomWidth: 1, borderBottomColor: C.border, backgroundColor: C.surface },
  hTitle: { fontSize: 20, fontWeight: '800', color: C.primary, letterSpacing: 3 },
  hSub: { fontSize: 9, color: C.textTer, letterSpacing: 1.5, marginTop: 2 },
  hBtn: { width: 36, height: 36, borderRadius: 8, borderWidth: 1, borderColor: C.border, alignItems: 'center', justifyContent: 'center' },
  addBox: { padding: 16, backgroundColor: C.surface, borderBottomWidth: 1, borderBottomColor: C.border },
  catRow: { flexDirection: 'row', gap: 8, marginBottom: 10 },
  catBtn: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 6, borderWidth: 1, borderColor: C.border },
  catBtnText: { fontSize: 11, color: C.textTer, fontWeight: '600', textTransform: 'capitalize' },
  input: { backgroundColor: C.surfEl, borderWidth: 1, borderColor: C.border, borderRadius: 10, paddingHorizontal: 14, paddingVertical: 10, color: C.text, fontSize: 14, marginBottom: 8 },
  saveBtn: { backgroundColor: C.primary, borderRadius: 8, paddingVertical: 12, alignItems: 'center' },
  saveBtnText: { fontSize: 14, fontWeight: '700', color: '#000' },
  loadBox: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  emptyBox: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 40 },
  emptyTitle: { fontSize: 18, fontWeight: '600', color: C.textSec, marginTop: 16, marginBottom: 8 },
  emptySub: { fontSize: 13, color: C.textTer, textAlign: 'center', lineHeight: 20 },
  listContent: { padding: 16, paddingBottom: 32 },
  catSection: { marginBottom: 20 },
  catHeader: { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 8 },
  catDot: { width: 8, height: 8, borderRadius: 4 },
  catLabel: { fontSize: 11, fontWeight: '700', letterSpacing: 1.5 },
  catCount: { fontSize: 10, color: C.textTer, marginLeft: 'auto' },
  memCard: { backgroundColor: C.surfEl, borderWidth: 1, borderColor: C.border, borderRadius: 10, padding: 12, marginBottom: 6 },
  memKey: { fontSize: 13, fontWeight: '600', color: C.text, marginBottom: 2 },
  memVal: { fontSize: 13, color: C.textSec, lineHeight: 18 },
  memSource: { fontSize: 9, color: C.textTer, marginTop: 4, letterSpacing: 0.5, textTransform: 'uppercase' },
});
