import React, { useState, useCallback } from 'react';
import { View, Text, TouchableOpacity, FlatList, ScrollView, StyleSheet, TextInput, Alert, ActivityIndicator } from 'react-native';
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

const TYPE_META: Record<string, { icon: string; color: string; label: string; desc: string }> = {
  identity: { icon: 'person', color: '#FFB800', label: 'Identity', desc: 'Name, people, dates, relationships' },
  episodic: { icon: 'calendar', color: '#3B82F6', label: 'Episodic', desc: 'Events, decisions, experiences' },
  semantic: { icon: 'book', color: '#00FFCC', label: 'Semantic', desc: 'Facts, knowledge, preferences' },
  behavioral: { icon: 'pulse', color: '#A855F7', label: 'Behavioral', desc: 'Habits, routines, patterns' },
  emotional: { icon: 'heart', color: '#EC4899', label: 'Emotional', desc: 'Feelings, reactions, context' },
  relationship: { icon: 'people', color: '#F59E0B', label: 'Relationship', desc: 'Connections between people' },
};

export default function MemoryScreen() {
  const [memData, setMemData] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [selectedType, setSelectedType] = useState<string | null>(null);
  const [showAdd, setShowAdd] = useState(false);
  const [addType, setAddType] = useState('semantic');
  const [addKey, setAddKey] = useState('');
  const [addVal, setAddVal] = useState('');
  const [explaining, setExplaining] = useState<string | null>(null);
  const [explanation, setExplanation] = useState<any>(null);

  useFocusEffect(useCallback(() => { load(); }, []));

  const load = async () => {
    try {
      const r = await fetch(`${API}/api/memory/os`);
      const d = await r.json();
      setMemData(d);
    } catch (e) { console.error(e); } finally { setLoading(false); }
  };

  const addMemory = async () => {
    if (!addKey.trim() || !addVal.trim()) return;
    await fetch(`${API}/api/memory/advanced`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mem_type: addType, category: addType, key: addKey, value: addVal, confidence: 0.9, why: 'Added manually' }),
    });
    setAddKey(''); setAddVal(''); setShowAdd(false); load();
  };

  const forgetMemory = (id: string, key: string) => {
    Alert.alert('Forget this?', `Remove "${key}" from memory? This is logged in the Trust Ledger.`, [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Forget', style: 'destructive', onPress: async () => {
        await fetch(`${API}/api/memory/forget/${id}`, { method: 'POST' }); load();
      }},
    ]);
  };

  const explainMemory = async (id: string) => {
    setExplaining(id); setExplanation(null);
    const r = await fetch(`${API}/api/memory/explain/${id}`);
    const d = await r.json();
    setExplanation(d); setExplaining(null);
  };

  const stats = memData?.stats || {};
  const allTypes = Object.keys(TYPE_META);
  const displayMems = selectedType
    ? (memData?.memories?.[selectedType] || [])
    : allTypes.flatMap(t => memData?.memories?.[t] || []);

  return (
    <SafeAreaView style={st.container} edges={['top']}>
      <View testID="mem-header" style={st.header}>
        <View>
          <Text style={st.hTitle}>MEMORY OS</Text>
          <Text style={st.hSub}>{stats.total || 0} MEMORIES • 6 INTELLIGENCE TYPES</Text>
        </View>
        <TouchableOpacity testID="add-mem-btn" onPress={() => setShowAdd(!showAdd)} style={st.hBtn}>
          <Ionicons name={showAdd ? 'close' : 'add'} size={20} color={C.primary} />
        </TouchableOpacity>
      </View>

      {/* Type Chips */}
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={st.chipRow} contentContainerStyle={st.chipContent}>
        <TouchableOpacity style={[st.chip, !selectedType && st.chipActive]} onPress={() => setSelectedType(null)}>
          <Text style={[st.chipText, !selectedType && st.chipTextActive]}>All ({stats.total || 0})</Text>
        </TouchableOpacity>
        {allTypes.map(t => {
          const meta = TYPE_META[t];
          const count = stats[t] || 0;
          return (
            <TouchableOpacity key={t} testID={`type-${t}`}
              style={[st.chip, selectedType === t && { borderColor: meta.color, backgroundColor: meta.color + '15' }]}
              onPress={() => setSelectedType(selectedType === t ? null : t)}>
              <Ionicons name={meta.icon as any} size={12} color={selectedType === t ? meta.color : C.textTer} />
              <Text style={[st.chipText, selectedType === t && { color: meta.color }]}>{meta.label} ({count})</Text>
            </TouchableOpacity>
          );
        })}
      </ScrollView>

      {/* Add Form */}
      {showAdd && (
        <View style={st.addBox}>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginBottom: 8 }}>
            {allTypes.map(t => (
              <TouchableOpacity key={t} style={[st.typeBtn, addType === t && { borderColor: TYPE_META[t].color, backgroundColor: TYPE_META[t].color + '15' }]}
                onPress={() => setAddType(t)}>
                <Text style={[st.typeBtnText, addType === t && { color: TYPE_META[t].color }]}>{TYPE_META[t].label}</Text>
              </TouchableOpacity>
            ))}
          </ScrollView>
          <TextInput testID="mem-key" style={st.input} placeholder="What (e.g., Favorite restaurant)" placeholderTextColor={C.textTer} value={addKey} onChangeText={setAddKey} />
          <TextInput testID="mem-val" style={st.input} placeholder="Detail (e.g., Olive Garden)" placeholderTextColor={C.textTer} value={addVal} onChangeText={setAddVal} />
          <TouchableOpacity testID="save-mem-btn" style={st.saveBtn} onPress={addMemory}><Text style={st.saveBtnText}>Store in Memory OS</Text></TouchableOpacity>
        </View>
      )}

      {/* Explanation Panel */}
      {explanation && (
        <View style={st.explainBox}>
          <View style={st.explainHeader}>
            <Text style={st.explainTitle}>Why I Remember This</Text>
            <TouchableOpacity onPress={() => setExplanation(null)}><Ionicons name="close" size={18} color={C.textTer} /></TouchableOpacity>
          </View>
          <Text style={st.explainRow}>📝 Reason: {explanation.explanation?.why}</Text>
          <Text style={st.explainRow}>📡 Source: {explanation.explanation?.source}</Text>
          <Text style={st.explainRow}>🎯 Confidence: {Math.round((explanation.explanation?.confidence || 0) * 100)}%</Text>
          <Text style={st.explainRow}>📅 Learned: {explanation.explanation?.first_learned?.slice(0, 10)}</Text>
          <Text style={st.explainRow}>🔄 Used: {explanation.explanation?.times_used || 0} times</Text>
        </View>
      )}

      {/* Memories List */}
      {loading ? (
        <View style={st.center}><ActivityIndicator size="large" color={C.primary} /></View>
      ) : displayMems.length === 0 ? (
        <View style={st.center}>
          <Ionicons name="bulb" size={48} color={C.textTer} />
          <Text style={st.emptyTitle}>No Memories Yet</Text>
          <Text style={st.emptySub}>Chat with Nua — she'll remember what matters.{'\n'}Say: "My name is...", "I prefer...", "I work at..."</Text>
        </View>
      ) : (
        <FlatList testID="mem-list" data={displayMems} keyExtractor={item => item.id}
          contentContainerStyle={st.listContent}
          renderItem={({ item }) => {
            const meta = TYPE_META[item.mem_type || item.category] || TYPE_META.semantic;
            return (
              <View testID={`mem-${item.id}`} style={st.memCard}>
                <View style={[st.memIcon, { borderColor: meta.color + '30' }]}>
                  <Ionicons name={meta.icon as any} size={16} color={meta.color} />
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={st.memKey}>{item.key}</Text>
                  <Text style={st.memVal}>{item.value}</Text>
                  <View style={st.memMeta}>
                    <Text style={[st.memType, { color: meta.color }]}>{meta.label}</Text>
                    <Text style={st.memConf}>{Math.round((item.confidence || 0.8) * 100)}% confident</Text>
                  </View>
                </View>
                <View style={st.memActions}>
                  <TouchableOpacity testID={`explain-${item.id}`} onPress={() => explainMemory(item.id)} style={st.memActBtn}>
                    {explaining === item.id ? <ActivityIndicator size="small" color={C.primary} /> : <Ionicons name="help-circle" size={18} color={C.primary} />}
                  </TouchableOpacity>
                  <TouchableOpacity testID={`forget-${item.id}`} onPress={() => forgetMemory(item.id, item.key)} style={st.memActBtn}>
                    <Ionicons name="trash-outline" size={16} color={C.err} />
                  </TouchableOpacity>
                </View>
              </View>
            );
          }}
          showsVerticalScrollIndicator={false}
        />
      )}
    </SafeAreaView>
  );
}

const st = StyleSheet.create({
  container: { flex: 1, backgroundColor: C.bg },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 40 },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 20, paddingVertical: 14, borderBottomWidth: 1, borderBottomColor: C.border, backgroundColor: C.surface },
  hTitle: { fontSize: 20, fontWeight: '800', color: C.primary, letterSpacing: 3 },
  hSub: { fontSize: 8, color: C.textTer, letterSpacing: 1, marginTop: 2 },
  hBtn: { width: 36, height: 36, borderRadius: 8, borderWidth: 1, borderColor: C.border, alignItems: 'center', justifyContent: 'center' },
  chipRow: { maxHeight: 56, borderBottomWidth: 1, borderBottomColor: C.border, backgroundColor: C.surface },
  chipContent: { paddingHorizontal: 12, paddingVertical: 10, gap: 6 },
  chip: { flexDirection: 'row', alignItems: 'center', gap: 4, paddingHorizontal: 10, paddingVertical: 6, borderRadius: 14, borderWidth: 1, borderColor: C.border, flexShrink: 0 },
  chipActive: { borderColor: C.primary, backgroundColor: 'rgba(255,184,0,0.1)' },
  chipText: { fontSize: 11, color: C.textTer, fontWeight: '600' },
  chipTextActive: { color: C.primary },
  addBox: { padding: 14, backgroundColor: C.surface, borderBottomWidth: 1, borderBottomColor: C.border },
  typeBtn: { paddingHorizontal: 10, paddingVertical: 5, borderRadius: 6, borderWidth: 1, borderColor: C.border, marginRight: 6 },
  typeBtnText: { fontSize: 10, color: C.textTer, fontWeight: '600' },
  input: { backgroundColor: C.surfEl, borderWidth: 1, borderColor: C.border, borderRadius: 8, paddingHorizontal: 12, paddingVertical: 9, color: C.text, fontSize: 13, marginBottom: 6 },
  saveBtn: { backgroundColor: C.primary, borderRadius: 8, paddingVertical: 10, alignItems: 'center' },
  saveBtnText: { fontSize: 13, fontWeight: '700', color: '#000' },
  explainBox: { backgroundColor: 'rgba(255,184,0,0.04)', borderWidth: 1, borderColor: C.hudBorder, margin: 12, borderRadius: 10, padding: 12 },
  explainHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 },
  explainTitle: { fontSize: 13, fontWeight: '700', color: C.primary },
  explainRow: { fontSize: 12, color: C.textSec, lineHeight: 20 },
  listContent: { padding: 12, paddingBottom: 32 },
  memCard: { flexDirection: 'row', alignItems: 'center', gap: 10, backgroundColor: C.surfEl, borderWidth: 1, borderColor: C.border, borderRadius: 10, padding: 12, marginBottom: 6 },
  memIcon: { width: 34, height: 34, borderRadius: 10, borderWidth: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(255,255,255,0.02)' },
  memKey: { fontSize: 13, fontWeight: '600', color: C.text },
  memVal: { fontSize: 12, color: C.textSec, marginTop: 1 },
  memMeta: { flexDirection: 'row', gap: 8, marginTop: 3 },
  memType: { fontSize: 9, fontWeight: '700', letterSpacing: 0.5, textTransform: 'uppercase' },
  memConf: { fontSize: 9, color: C.textTer },
  memActions: { gap: 6 },
  memActBtn: { padding: 4 },
  emptyTitle: { fontSize: 18, fontWeight: '600', color: C.textSec, marginTop: 16, marginBottom: 8 },
  emptySub: { fontSize: 12, color: C.textTer, textAlign: 'center', lineHeight: 20 },
});
