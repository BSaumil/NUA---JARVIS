import React, { useState, useCallback } from 'react';
import { View, Text, TouchableOpacity, ScrollView, StyleSheet, TextInput, Alert, ActivityIndicator } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from 'expo-router';

const API = process.env.EXPO_PUBLIC_BACKEND_URL;
const C = {
  bg: '#050505', surface: '#0F0F0F', surfEl: '#1A1A18',
  primary: '#FFB800', accent: '#00FFCC',
  text: '#FFFFFF', textSec: '#A1A1AA', textTer: '#71717A',
  border: '#1E1E1E', hudBorder: 'rgba(255,184,0,0.15)',
  ok: '#10B981', warn: '#F59E0B', err: '#EF4444', info: '#3B82F6',
};

type Tab = 'goals' | 'dreams' | 'reminders' | 'notes' | 'expenses';

const DREAM_COLORS: Record<string, string> = {
  opportunity: '#FFB800', pattern: '#3B82F6', reminder: '#F59E0B', concern: '#EF4444',
  optimization: '#00FFCC', relationship: '#EC4899', finance: '#10B981',
  productivity: '#A855F7', learning: '#06B6D4', business: '#FF4500',
};

export default function LifeScreen() {
  const [tab, setTab] = useState<Tab>('goals');
  const [goals, setGoals] = useState<any[]>([]);
  const [dreams, setDreams] = useState<any[]>([]);
  const [reminders, setReminders] = useState<any[]>([]);
  const [notes, setNotes] = useState<any[]>([]);
  const [expenses, setExpenses] = useState<any[]>([]);
  const [expSummary, setExpSummary] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [showAdd, setShowAdd] = useState(false);
  const [dreamLoading, setDreamLoading] = useState(false);
  const [analyzingGoal, setAnalyzingGoal] = useState<string | null>(null);
  const [goalInsight, setGoalInsight] = useState<string | null>(null);
  // Form fields
  const [goalTitle, setGoalTitle] = useState('');
  const [goalDesc, setGoalDesc] = useState('');
  const [subtaskTitle, setSubtaskTitle] = useState('');
  const [addingSubTo, setAddingSubTo] = useState<string | null>(null);
  const [remTitle, setRemTitle] = useState('');
  const [noteTitle, setNoteTitle] = useState('');
  const [noteContent, setNoteContent] = useState('');
  const [expAmount, setExpAmount] = useState('');
  const [expCat, setExpCat] = useState('');

  useFocusEffect(useCallback(() => { loadAll(); }, []));

  const loadAll = async () => {
    setLoading(true);
    try {
      const [g, d, r, n, e, es] = await Promise.all([
        fetch(`${API}/api/goals`).then(r => r.json()),
        fetch(`${API}/api/dreams`).then(r => r.json()),
        fetch(`${API}/api/reminders`).then(r => r.json()),
        fetch(`${API}/api/notes`).then(r => r.json()),
        fetch(`${API}/api/expenses`).then(r => r.json()),
        fetch(`${API}/api/expenses/summary`).then(r => r.json()),
      ]);
      setGoals(g.goals || []); setDreams(d.dreams || []);
      setReminders(r.reminders || []); setNotes(n.notes || []);
      setExpenses(e.expenses || []); setExpSummary(es);
    } catch (e) { console.error(e); } finally { setLoading(false); }
  };

  const addGoal = async () => {
    if (!goalTitle.trim()) return;
    await fetch(`${API}/api/goals`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ title: goalTitle, description: goalDesc }) });
    setGoalTitle(''); setGoalDesc(''); setShowAdd(false); loadAll();
  };

  const addSubtask = async (goalId: string) => {
    if (!subtaskTitle.trim()) return;
    await fetch(`${API}/api/goals/${goalId}/subtask`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ title: subtaskTitle }) });
    setSubtaskTitle(''); setAddingSubTo(null); loadAll();
  };

  const toggleSubtask = async (goalId: string, subtaskId: string) => {
    await fetch(`${API}/api/goals/${goalId}/subtask/${subtaskId}`, { method: 'PATCH' }); loadAll();
  };

  const analyzeGoal = async (goalId: string) => {
    setAnalyzingGoal(goalId); setGoalInsight(null);
    const r = await fetch(`${API}/api/goals/${goalId}/analyze`, { method: 'POST' });
    const d = await r.json();
    setGoalInsight(d.insight || 'No insight generated.'); setAnalyzingGoal(null);
  };

  const generateDream = async () => {
    setDreamLoading(true);
    try {
      await fetch(`${API}/api/dreams/generate`, { method: 'POST' }); loadAll();
    } catch (e) { console.error(e); } finally { setDreamLoading(false); }
  };

  const addItem = async () => {
    if (tab === 'reminders' && remTitle.trim()) {
      await fetch(`${API}/api/reminders`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ title: remTitle }) });
      setRemTitle('');
    } else if (tab === 'notes' && noteTitle.trim()) {
      await fetch(`${API}/api/notes`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ title: noteTitle, content: noteContent }) });
      setNoteTitle(''); setNoteContent('');
    } else if (tab === 'expenses' && expAmount && expCat.trim()) {
      await fetch(`${API}/api/expenses`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ amount: parseFloat(expAmount), category: expCat }) });
      setExpAmount(''); setExpCat('');
    }
    setShowAdd(false); loadAll();
  };

  const deleteItem = (type: string, id: string) => {
    Alert.alert('Delete?', 'Remove this item?', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Delete', style: 'destructive', onPress: async () => {
        await fetch(`${API}/api/${type}/${id}`, { method: 'DELETE' }); loadAll();
      }},
    ]);
  };

  const toggleReminder = async (id: string) => { await fetch(`${API}/api/reminders/${id}`, { method: 'PATCH' }); loadAll(); };

  const TABS: { id: Tab; icon: string; label: string }[] = [
    { id: 'goals', icon: 'flag', label: 'Goals' },
    { id: 'dreams', icon: 'sparkles', label: 'Dreams' },
    { id: 'reminders', icon: 'alarm', label: 'Reminders' },
    { id: 'notes', icon: 'document-text', label: 'Notes' },
    { id: 'expenses', icon: 'wallet', label: 'Expenses' },
  ];

  return (
    <SafeAreaView style={st.container} edges={['top']}>
      <View testID="life-header" style={st.header}>
        <Text style={st.hTitle}>LIFE HUB</Text>
        <Text style={st.hSub}>GOALS • DREAMS • LIFE MANAGEMENT</Text>
      </View>

      {/* Tabs */}
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={st.tabRow} contentContainerStyle={st.tabContent}>
        {TABS.map(t => (
          <TouchableOpacity key={t.id} testID={`tab-${t.id}`}
            style={[st.tabBtn, tab === t.id && st.tabActive]}
            onPress={() => { setTab(t.id); setShowAdd(false); setGoalInsight(null); }}>
            <Ionicons name={t.icon as any} size={14} color={tab === t.id ? C.primary : C.textTer} />
            <Text style={[st.tabText, tab === t.id && st.tabTextActive]}>{t.label}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>

      {loading ? (
        <View style={st.center}><ActivityIndicator size="large" color={C.primary} /></View>
      ) : (
        <ScrollView contentContainerStyle={st.scrollContent} showsVerticalScrollIndicator={false}>

          {/* === GOALS === */}
          {tab === 'goals' && (<>
            <TouchableOpacity testID="add-goal-btn" style={st.addBtn} onPress={() => setShowAdd(!showAdd)}>
              <Ionicons name="add" size={18} color={C.primary} /><Text style={st.addBtnText}>New Goal</Text>
            </TouchableOpacity>
            {showAdd && (
              <View style={st.formBox}>
                <TextInput testID="goal-title" style={st.input} placeholder="Goal title" placeholderTextColor={C.textTer} value={goalTitle} onChangeText={setGoalTitle} />
                <TextInput testID="goal-desc" style={st.input} placeholder="Description (optional)" placeholderTextColor={C.textTer} value={goalDesc} onChangeText={setGoalDesc} />
                <TouchableOpacity style={st.saveBtn} onPress={addGoal}><Text style={st.saveBtnText}>Create Goal</Text></TouchableOpacity>
              </View>
            )}
            {goalInsight && (
              <View style={st.insightBox}>
                <Text style={st.insightTitle}>🧠 NUA Analysis</Text>
                <Text style={st.insightText}>{goalInsight}</Text>
                <TouchableOpacity onPress={() => setGoalInsight(null)}><Text style={st.dismissText}>Dismiss</Text></TouchableOpacity>
              </View>
            )}
            {goals.length === 0 ? (
              <View style={st.emptyBox}><Ionicons name="flag" size={40} color={C.textTer} /><Text style={st.emptyText}>No goals yet</Text><Text style={st.emptyHint}>Set goals and let Nua help you achieve them</Text></View>
            ) : goals.map(g => (
              <View key={g.id} testID={`goal-${g.id}`} style={st.goalCard}>
                <View style={st.goalHeader}>
                  <View style={{ flex: 1 }}>
                    <Text style={st.goalTitle}>{g.title}</Text>
                    {g.description ? <Text style={st.goalDesc}>{g.description}</Text> : null}
                  </View>
                  <View style={st.goalActions}>
                    <TouchableOpacity testID={`analyze-${g.id}`} onPress={() => analyzeGoal(g.id)} style={st.miniBtn}>
                      {analyzingGoal === g.id ? <ActivityIndicator size="small" color={C.primary} /> : <Ionicons name="bulb" size={16} color={C.primary} />}
                    </TouchableOpacity>
                    <TouchableOpacity onPress={() => deleteItem('goals', g.id)} style={st.miniBtn}><Ionicons name="trash-outline" size={14} color={C.err} /></TouchableOpacity>
                  </View>
                </View>
                {/* Progress */}
                <View style={st.progressRow}>
                  <View style={st.progressBar}><View style={[st.progressFill, { width: `${g.progress}%` }]} /></View>
                  <Text style={st.progressText}>{g.progress}%</Text>
                </View>
                {/* Sub-tasks */}
                {(g.sub_tasks || []).map((s: any) => (
                  <TouchableOpacity key={s.id} style={st.subtaskRow} onPress={() => toggleSubtask(g.id, s.id)}>
                    <Ionicons name={s.completed ? 'checkmark-circle' : 'ellipse-outline'} size={18} color={s.completed ? C.ok : C.textTer} />
                    <Text style={[st.subtaskText, s.completed && st.completed]}>{s.title}</Text>
                  </TouchableOpacity>
                ))}
                {addingSubTo === g.id ? (
                  <View style={st.subtaskAdd}>
                    <TextInput style={[st.input, { flex: 1, marginBottom: 0 }]} placeholder="Sub-task title" placeholderTextColor={C.textTer} value={subtaskTitle} onChangeText={setSubtaskTitle} />
                    <TouchableOpacity onPress={() => addSubtask(g.id)} style={st.miniSaveBtn}><Ionicons name="checkmark" size={18} color={C.primary} /></TouchableOpacity>
                  </View>
                ) : (
                  <TouchableOpacity style={st.addSubBtn} onPress={() => setAddingSubTo(g.id)}>
                    <Ionicons name="add" size={14} color={C.textTer} /><Text style={st.addSubText}>Add sub-task</Text>
                  </TouchableOpacity>
                )}
              </View>
            ))}
          </>)}

          {/* === DREAMS === */}
          {tab === 'dreams' && (<>
            <TouchableOpacity testID="gen-dream-btn" style={st.dreamGenBtn} onPress={generateDream} disabled={dreamLoading}>
              {dreamLoading ? <ActivityIndicator size="small" color="#000" /> : <Ionicons name="sparkles" size={18} color="#000" />}
              <Text style={st.dreamGenText}>{dreamLoading ? 'Dreaming...' : 'Generate New Dream'}</Text>
            </TouchableOpacity>
            <Text style={st.dreamHint}>Nua cross-references all your data to find non-obvious insights</Text>
            {dreams.length === 0 ? (
              <View style={st.emptyBox}><Ionicons name="sparkles" size={40} color={C.textTer} /><Text style={st.emptyText}>No dreams yet</Text><Text style={st.emptyHint}>Generate a dream to get cross-referenced insights from your data</Text></View>
            ) : dreams.map(d => (
              <View key={d.id} testID={`dream-${d.id}`} style={[st.dreamCard, { borderLeftColor: DREAM_COLORS[d.category] || C.primary }]}>
                <View style={st.dreamHeader}>
                  <View style={[st.dreamCatBadge, { backgroundColor: (DREAM_COLORS[d.category] || C.primary) + '20' }]}>
                    <Text style={[st.dreamCatText, { color: DREAM_COLORS[d.category] || C.primary }]}>{d.category?.toUpperCase()}</Text>
                  </View>
                  {d.impact && <Text style={[st.impactBadge, d.impact === 'high' && { color: C.err }]}>{d.impact} impact</Text>}
                </View>
                <Text style={st.dreamTitle}>{d.title}</Text>
                <Text style={st.dreamContent}>{d.content}</Text>
                <Text style={st.dreamDate}>{d.created_at?.slice(0, 10)}</Text>
              </View>
            ))}
          </>)}

          {/* === REMINDERS === */}
          {tab === 'reminders' && (<>
            <TouchableOpacity style={st.addBtn} onPress={() => setShowAdd(!showAdd)}><Ionicons name="add" size={18} color={C.primary} /><Text style={st.addBtnText}>New Reminder</Text></TouchableOpacity>
            {showAdd && (<View style={st.formBox}><TextInput style={st.input} placeholder="Reminder title" placeholderTextColor={C.textTer} value={remTitle} onChangeText={setRemTitle} /><TouchableOpacity style={st.saveBtn} onPress={addItem}><Text style={st.saveBtnText}>Add Reminder</Text></TouchableOpacity></View>)}
            {reminders.length === 0 ? <View style={st.emptyBox}><Text style={st.emptyText}>No reminders</Text></View> :
            reminders.map(r => (
              <TouchableOpacity key={r.id} style={st.itemCard} onPress={() => toggleReminder(r.id)} onLongPress={() => deleteItem('reminders', r.id)}>
                <Ionicons name={r.completed ? 'checkmark-circle' : 'ellipse-outline'} size={20} color={r.completed ? C.ok : C.warn} />
                <Text style={[st.itemTitle, r.completed && st.completed]}>{r.title}</Text>
              </TouchableOpacity>
            ))}
          </>)}

          {/* === NOTES === */}
          {tab === 'notes' && (<>
            <TouchableOpacity style={st.addBtn} onPress={() => setShowAdd(!showAdd)}><Ionicons name="add" size={18} color={C.primary} /><Text style={st.addBtnText}>New Note</Text></TouchableOpacity>
            {showAdd && (<View style={st.formBox}><TextInput style={st.input} placeholder="Note title" placeholderTextColor={C.textTer} value={noteTitle} onChangeText={setNoteTitle} /><TextInput style={[st.input, { height: 60 }]} placeholder="Content" placeholderTextColor={C.textTer} value={noteContent} onChangeText={setNoteContent} multiline /><TouchableOpacity style={st.saveBtn} onPress={addItem}><Text style={st.saveBtnText}>Save Note</Text></TouchableOpacity></View>)}
            {notes.length === 0 ? <View style={st.emptyBox}><Text style={st.emptyText}>No notes</Text></View> :
            notes.map(n => (
              <TouchableOpacity key={n.id} style={st.itemCard} onLongPress={() => deleteItem('notes', n.id)}>
                <Ionicons name="document-text" size={18} color={C.info} />
                <View style={{ flex: 1 }}><Text style={st.itemTitle}>{n.title}</Text><Text style={st.itemSub} numberOfLines={2}>{n.content}</Text></View>
              </TouchableOpacity>
            ))}
          </>)}

          {/* === EXPENSES === */}
          {tab === 'expenses' && (<>
            {expSummary && (<View style={st.expSummary}><Text style={st.expTotal}>${(expSummary.total || 0).toFixed(2)}</Text><Text style={st.expLabel}>TOTAL SPENT</Text></View>)}
            <TouchableOpacity style={st.addBtn} onPress={() => setShowAdd(!showAdd)}><Ionicons name="add" size={18} color={C.primary} /><Text style={st.addBtnText}>Log Expense</Text></TouchableOpacity>
            {showAdd && (<View style={st.formBox}><TextInput style={st.input} placeholder="Amount" placeholderTextColor={C.textTer} value={expAmount} onChangeText={setExpAmount} keyboardType="numeric" /><TextInput style={st.input} placeholder="Category" placeholderTextColor={C.textTer} value={expCat} onChangeText={setExpCat} /><TouchableOpacity style={st.saveBtn} onPress={addItem}><Text style={st.saveBtnText}>Log Expense</Text></TouchableOpacity></View>)}
            {expenses.length === 0 ? <View style={st.emptyBox}><Text style={st.emptyText}>No expenses</Text></View> :
            expenses.map(e => (
              <TouchableOpacity key={e.id} style={st.itemCard} onLongPress={() => deleteItem('expenses', e.id)}>
                <View style={st.expBadge}><Text style={st.expBadgeText}>${Number(e.amount).toFixed(2)}</Text></View>
                <View style={{ flex: 1 }}><Text style={st.itemTitle}>{e.category}</Text><Text style={st.itemSub}>{e.date}</Text></View>
              </TouchableOpacity>
            ))}
          </>)}
          <View style={{ height: 32 }} />
        </ScrollView>
      )}
    </SafeAreaView>
  );
}

const st = StyleSheet.create({
  container: { flex: 1, backgroundColor: C.bg },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  header: { paddingHorizontal: 20, paddingVertical: 14, borderBottomWidth: 1, borderBottomColor: C.border, backgroundColor: C.surface },
  hTitle: { fontSize: 20, fontWeight: '800', color: C.primary, letterSpacing: 3 },
  hSub: { fontSize: 8, color: C.textTer, letterSpacing: 1, marginTop: 2 },
  tabRow: { maxHeight: 56, borderBottomWidth: 1, borderBottomColor: C.border, backgroundColor: C.surface },
  tabContent: { paddingHorizontal: 8, paddingVertical: 10, gap: 4 },
  tabBtn: { flexDirection: 'row', alignItems: 'center', gap: 4, paddingHorizontal: 12, paddingVertical: 7, borderRadius: 14, flexShrink: 0 },
  tabActive: { backgroundColor: 'rgba(255,184,0,0.1)' },
  tabText: { fontSize: 11, color: C.textTer, fontWeight: '600' },
  tabTextActive: { color: C.primary },
  scrollContent: { padding: 14 },
  addBtn: { flexDirection: 'row', alignItems: 'center', gap: 6, borderWidth: 1, borderColor: C.hudBorder, borderRadius: 8, padding: 10, marginBottom: 10, justifyContent: 'center' },
  addBtnText: { fontSize: 12, color: C.primary, fontWeight: '600' },
  formBox: { backgroundColor: C.surface, borderRadius: 10, padding: 12, marginBottom: 12, borderWidth: 1, borderColor: C.border },
  input: { backgroundColor: C.surfEl, borderWidth: 1, borderColor: C.border, borderRadius: 8, paddingHorizontal: 12, paddingVertical: 8, color: C.text, fontSize: 13, marginBottom: 6 },
  saveBtn: { backgroundColor: C.primary, borderRadius: 8, paddingVertical: 10, alignItems: 'center' },
  saveBtnText: { fontSize: 13, fontWeight: '700', color: '#000' },
  insightBox: { backgroundColor: 'rgba(255,184,0,0.04)', borderWidth: 1, borderColor: C.hudBorder, borderRadius: 10, padding: 12, marginBottom: 12 },
  insightTitle: { fontSize: 13, fontWeight: '700', color: C.primary, marginBottom: 6 },
  insightText: { fontSize: 12, color: C.textSec, lineHeight: 18 },
  dismissText: { fontSize: 11, color: C.textTer, marginTop: 8 },
  goalCard: { backgroundColor: C.surfEl, borderWidth: 1, borderColor: C.border, borderRadius: 12, padding: 14, marginBottom: 10 },
  goalHeader: { flexDirection: 'row', justifyContent: 'space-between' },
  goalTitle: { fontSize: 15, fontWeight: '700', color: C.text },
  goalDesc: { fontSize: 11, color: C.textSec, marginTop: 2 },
  goalActions: { flexDirection: 'row', gap: 6 },
  miniBtn: { padding: 4 },
  progressRow: { flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 10 },
  progressBar: { flex: 1, height: 6, backgroundColor: C.border, borderRadius: 3 },
  progressFill: { height: 6, backgroundColor: C.primary, borderRadius: 3 },
  progressText: { fontSize: 11, color: C.primary, fontWeight: '700', width: 32, textAlign: 'right' },
  subtaskRow: { flexDirection: 'row', alignItems: 'center', gap: 8, paddingVertical: 6, marginTop: 4 },
  subtaskText: { fontSize: 13, color: C.text },
  completed: { textDecorationLine: 'line-through', color: C.textTer },
  subtaskAdd: { flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 6 },
  miniSaveBtn: { padding: 8, borderWidth: 1, borderColor: C.primary, borderRadius: 8 },
  addSubBtn: { flexDirection: 'row', alignItems: 'center', gap: 4, paddingVertical: 6, marginTop: 4 },
  addSubText: { fontSize: 11, color: C.textTer },
  dreamGenBtn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, backgroundColor: C.primary, borderRadius: 10, paddingVertical: 14, marginBottom: 8 },
  dreamGenText: { fontSize: 14, fontWeight: '700', color: '#000' },
  dreamHint: { fontSize: 10, color: C.textTer, textAlign: 'center', marginBottom: 16 },
  dreamCard: { backgroundColor: C.surfEl, borderWidth: 1, borderColor: C.border, borderLeftWidth: 3, borderRadius: 10, padding: 14, marginBottom: 10 },
  dreamHeader: { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 6 },
  dreamCatBadge: { borderRadius: 4, paddingHorizontal: 6, paddingVertical: 2 },
  dreamCatText: { fontSize: 8, fontWeight: '700', letterSpacing: 1 },
  impactBadge: { fontSize: 9, color: C.textTer, marginLeft: 'auto' },
  dreamTitle: { fontSize: 14, fontWeight: '700', color: C.text, marginBottom: 4 },
  dreamContent: { fontSize: 12, color: C.textSec, lineHeight: 18 },
  dreamDate: { fontSize: 9, color: C.textTer, marginTop: 6 },
  emptyBox: { alignItems: 'center', paddingTop: 40 },
  emptyText: { fontSize: 16, fontWeight: '600', color: C.textSec, marginTop: 12 },
  emptyHint: { fontSize: 11, color: C.textTer, marginTop: 6, textAlign: 'center' },
  itemCard: { flexDirection: 'row', alignItems: 'center', gap: 10, backgroundColor: C.surfEl, borderWidth: 1, borderColor: C.border, borderRadius: 10, padding: 12, marginBottom: 6 },
  itemTitle: { fontSize: 13, fontWeight: '600', color: C.text },
  itemSub: { fontSize: 11, color: C.textTer, marginTop: 2 },
  expSummary: { alignItems: 'center', backgroundColor: C.surfEl, borderWidth: 1, borderColor: C.hudBorder, borderRadius: 12, padding: 14, marginBottom: 12 },
  expTotal: { fontSize: 28, fontWeight: '900', color: C.primary },
  expLabel: { fontSize: 9, color: C.textTer, letterSpacing: 2, marginTop: 2 },
  expBadge: { backgroundColor: 'rgba(239,68,68,0.1)', borderRadius: 6, paddingHorizontal: 8, paddingVertical: 4 },
  expBadgeText: { fontSize: 13, fontWeight: '800', color: C.err },
});
