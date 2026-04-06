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
  border: '#1E1E1E', hudBorder: 'rgba(255,184,0,0.15)',
  ok: '#10B981', warn: '#F59E0B', err: '#EF4444', info: '#3B82F6',
};

type Tab = 'reminders' | 'notes' | 'expenses';

export default function LifeScreen() {
  const [tab, setTab] = useState<Tab>('reminders');
  const [reminders, setReminders] = useState<any[]>([]);
  const [notes, setNotes] = useState<any[]>([]);
  const [expenses, setExpenses] = useState<any[]>([]);
  const [expSummary, setExpSummary] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [showAdd, setShowAdd] = useState(false);
  // Add form states
  const [remTitle, setRemTitle] = useState('');
  const [remDesc, setRemDesc] = useState('');
  const [remDate, setRemDate] = useState('');
  const [noteTitle, setNoteTitle] = useState('');
  const [noteContent, setNoteContent] = useState('');
  const [expAmount, setExpAmount] = useState('');
  const [expCat, setExpCat] = useState('');
  const [expDesc, setExpDesc] = useState('');

  useFocusEffect(useCallback(() => { loadAll(); }, []));

  const loadAll = async () => {
    setLoading(true);
    try {
      const [r1, r2, r3, r4] = await Promise.all([
        fetch(`${API}/api/reminders`).then(r => r.json()),
        fetch(`${API}/api/notes`).then(r => r.json()),
        fetch(`${API}/api/expenses`).then(r => r.json()),
        fetch(`${API}/api/expenses/summary`).then(r => r.json()),
      ]);
      setReminders(r1.reminders || []);
      setNotes(r2.notes || []);
      setExpenses(r3.expenses || []);
      setExpSummary(r4);
    } catch (e) { console.error(e); } finally { setLoading(false); }
  };

  const toggleReminder = async (id: string) => {
    await fetch(`${API}/api/reminders/${id}`, { method: 'PATCH' }); loadAll();
  };
  const deleteReminder = (id: string) => Alert.alert('Delete?', 'Remove this reminder?', [
    { text: 'Cancel', style: 'cancel' },
    { text: 'Delete', style: 'destructive', onPress: async () => { await fetch(`${API}/api/reminders/${id}`, { method: 'DELETE' }); loadAll(); } },
  ]);
  const deleteNote = (id: string) => Alert.alert('Delete?', 'Remove this note?', [
    { text: 'Cancel', style: 'cancel' },
    { text: 'Delete', style: 'destructive', onPress: async () => { await fetch(`${API}/api/notes/${id}`, { method: 'DELETE' }); loadAll(); } },
  ]);
  const deleteExpense = (id: string) => Alert.alert('Delete?', 'Remove this expense?', [
    { text: 'Cancel', style: 'cancel' },
    { text: 'Delete', style: 'destructive', onPress: async () => { await fetch(`${API}/api/expenses/${id}`, { method: 'DELETE' }); loadAll(); } },
  ]);

  const addItem = async () => {
    try {
      if (tab === 'reminders' && remTitle.trim()) {
        await fetch(`${API}/api/reminders`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ title: remTitle, description: remDesc, due_date: remDate || null }) });
        setRemTitle(''); setRemDesc(''); setRemDate('');
      } else if (tab === 'notes' && noteTitle.trim()) {
        await fetch(`${API}/api/notes`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ title: noteTitle, content: noteContent, tags: [] }) });
        setNoteTitle(''); setNoteContent('');
      } else if (tab === 'expenses' && expAmount && expCat.trim()) {
        await fetch(`${API}/api/expenses`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ amount: parseFloat(expAmount), category: expCat, description: expDesc }) });
        setExpAmount(''); setExpCat(''); setExpDesc('');
      }
      setShowAdd(false); loadAll();
    } catch (e) { console.error(e); }
  };

  const renderAddForm = () => {
    if (!showAdd) return null;
    if (tab === 'reminders') return (
      <View style={st.addBox}>
        <TextInput testID="rem-title" style={st.input} placeholder="Reminder title" placeholderTextColor={C.textTer} value={remTitle} onChangeText={setRemTitle} />
        <TextInput testID="rem-desc" style={st.input} placeholder="Description (optional)" placeholderTextColor={C.textTer} value={remDesc} onChangeText={setRemDesc} />
        <TextInput testID="rem-date" style={st.input} placeholder="Due date (YYYY-MM-DD)" placeholderTextColor={C.textTer} value={remDate} onChangeText={setRemDate} />
        <TouchableOpacity testID="add-rem-btn" style={st.saveBtn} onPress={addItem}><Text style={st.saveBtnText}>Add Reminder</Text></TouchableOpacity>
      </View>
    );
    if (tab === 'notes') return (
      <View style={st.addBox}>
        <TextInput testID="note-title" style={st.input} placeholder="Note title" placeholderTextColor={C.textTer} value={noteTitle} onChangeText={setNoteTitle} />
        <TextInput testID="note-content" style={[st.input, { height: 80 }]} placeholder="Content" placeholderTextColor={C.textTer} value={noteContent} onChangeText={setNoteContent} multiline />
        <TouchableOpacity testID="add-note-btn" style={st.saveBtn} onPress={addItem}><Text style={st.saveBtnText}>Save Note</Text></TouchableOpacity>
      </View>
    );
    return (
      <View style={st.addBox}>
        <TextInput testID="exp-amount" style={st.input} placeholder="Amount" placeholderTextColor={C.textTer} value={expAmount} onChangeText={setExpAmount} keyboardType="numeric" />
        <TextInput testID="exp-cat" style={st.input} placeholder="Category (food, transport...)" placeholderTextColor={C.textTer} value={expCat} onChangeText={setExpCat} />
        <TextInput testID="exp-desc" style={st.input} placeholder="Description (optional)" placeholderTextColor={C.textTer} value={expDesc} onChangeText={setExpDesc} />
        <TouchableOpacity testID="add-exp-btn" style={st.saveBtn} onPress={addItem}><Text style={st.saveBtnText}>Log Expense</Text></TouchableOpacity>
      </View>
    );
  };

  return (
    <SafeAreaView style={st.container} edges={['top']}>
      <View testID="life-header" style={st.header}>
        <View>
          <Text style={st.hTitle}>LIFE HUB</Text>
          <Text style={st.hSub}>REMINDERS • NOTES • EXPENSES</Text>
        </View>
        <TouchableOpacity testID="add-life-btn" onPress={() => setShowAdd(!showAdd)} style={st.hBtn}>
          <Ionicons name={showAdd ? 'close' : 'add'} size={20} color={C.primary} />
        </TouchableOpacity>
      </View>

      {/* Tabs */}
      <View style={st.tabs}>
        {(['reminders', 'notes', 'expenses'] as Tab[]).map(t => (
          <TouchableOpacity key={t} testID={`tab-${t}`} style={[st.tabBtn, tab === t && st.tabActive]} onPress={() => { setTab(t); setShowAdd(false); }}>
            <Ionicons name={t === 'reminders' ? 'alarm' : t === 'notes' ? 'document-text' : 'wallet'} size={16} color={tab === t ? C.primary : C.textTer} />
            <Text style={[st.tabText, tab === t && st.tabTextActive]}>{t}</Text>
          </TouchableOpacity>
        ))}
      </View>

      {renderAddForm()}

      {loading ? (
        <View style={st.loadBox}><ActivityIndicator size="large" color={C.primary} /></View>
      ) : (
        <ScrollView style={st.scroll} contentContainerStyle={st.scrollContent} showsVerticalScrollIndicator={false}>
          {/* Expenses Summary */}
          {tab === 'expenses' && expSummary && (
            <View style={st.summaryBox}>
              <Text style={st.summaryTotal}>${(expSummary.total || 0).toFixed(2)}</Text>
              <Text style={st.summaryLabel}>TOTAL SPENT</Text>
              {expSummary.by_category && Object.entries(expSummary.by_category).length > 0 && (
                <View style={st.catBreakdown}>
                  {Object.entries(expSummary.by_category).map(([cat, amt]: [string, any]) => (
                    <View key={cat} style={st.catRow}>
                      <Text style={st.catName}>{cat}</Text>
                      <Text style={st.catAmt}>${Number(amt).toFixed(2)}</Text>
                    </View>
                  ))}
                </View>
              )}
            </View>
          )}

          {/* Reminders */}
          {tab === 'reminders' && (reminders.length === 0 ? (
            <View style={st.emptyBox}>
              <Ionicons name="alarm" size={40} color={C.textTer} />
              <Text style={st.emptyText}>No reminders yet</Text>
              <Text style={st.emptyHint}>Tell Nua "Remind me to..." or add manually</Text>
            </View>
          ) : reminders.map(r => (
            <TouchableOpacity key={r.id} testID={`rem-${r.id}`} style={st.card} onPress={() => toggleReminder(r.id)} onLongPress={() => deleteReminder(r.id)}>
              <Ionicons name={r.completed ? 'checkmark-circle' : 'ellipse-outline'} size={22} color={r.completed ? C.ok : C.warn} />
              <View style={{ flex: 1 }}>
                <Text style={[st.cardTitle, r.completed && st.completed]}>{r.title}</Text>
                {r.description ? <Text style={st.cardSub}>{r.description}</Text> : null}
                {r.due_date ? <Text style={st.cardMeta}>Due: {r.due_date}</Text> : null}
              </View>
            </TouchableOpacity>
          )))}

          {/* Notes */}
          {tab === 'notes' && (notes.length === 0 ? (
            <View style={st.emptyBox}>
              <Ionicons name="document-text" size={40} color={C.textTer} />
              <Text style={st.emptyText}>No notes yet</Text>
              <Text style={st.emptyHint}>Tell Nua "Note that..." or add manually</Text>
            </View>
          ) : notes.map(n => (
            <TouchableOpacity key={n.id} testID={`note-${n.id}`} style={st.card} onLongPress={() => deleteNote(n.id)}>
              <Ionicons name="document-text" size={20} color={C.info} />
              <View style={{ flex: 1 }}>
                <Text style={st.cardTitle}>{n.title}</Text>
                <Text style={st.cardSub} numberOfLines={3}>{n.content}</Text>
                <Text style={st.cardMeta}>{new Date(n.created_at).toLocaleDateString()}</Text>
              </View>
            </TouchableOpacity>
          )))}

          {/* Expenses */}
          {tab === 'expenses' && (expenses.length === 0 ? (
            <View style={st.emptyBox}>
              <Ionicons name="wallet" size={40} color={C.textTer} />
              <Text style={st.emptyText}>No expenses logged</Text>
              <Text style={st.emptyHint}>Tell Nua "I spent $20 on lunch" or add manually</Text>
            </View>
          ) : expenses.map(e => (
            <TouchableOpacity key={e.id} testID={`exp-${e.id}`} style={st.card} onLongPress={() => deleteExpense(e.id)}>
              <View style={st.expAmtBox}><Text style={st.expAmt}>${Number(e.amount).toFixed(2)}</Text></View>
              <View style={{ flex: 1 }}>
                <Text style={st.cardTitle}>{e.category}</Text>
                {e.description ? <Text style={st.cardSub}>{e.description}</Text> : null}
                <Text style={st.cardMeta}>{e.date}</Text>
              </View>
            </TouchableOpacity>
          )))}
          <View style={{ height: 32 }} />
        </ScrollView>
      )}
    </SafeAreaView>
  );
}

const st = StyleSheet.create({
  container: { flex: 1, backgroundColor: C.bg },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 20, paddingVertical: 14, borderBottomWidth: 1, borderBottomColor: C.border, backgroundColor: C.surface },
  hTitle: { fontSize: 20, fontWeight: '800', color: C.primary, letterSpacing: 3 },
  hSub: { fontSize: 9, color: C.textTer, letterSpacing: 1, marginTop: 2 },
  hBtn: { width: 36, height: 36, borderRadius: 8, borderWidth: 1, borderColor: C.border, alignItems: 'center', justifyContent: 'center' },
  tabs: { flexDirection: 'row', backgroundColor: C.surface, borderBottomWidth: 1, borderBottomColor: C.border },
  tabBtn: { flex: 1, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, paddingVertical: 12 },
  tabActive: { borderBottomWidth: 2, borderBottomColor: C.primary },
  tabText: { fontSize: 11, color: C.textTer, fontWeight: '600', textTransform: 'uppercase' },
  tabTextActive: { color: C.primary },
  addBox: { padding: 16, backgroundColor: C.surface, borderBottomWidth: 1, borderBottomColor: C.border },
  input: { backgroundColor: C.surfEl, borderWidth: 1, borderColor: C.border, borderRadius: 10, paddingHorizontal: 14, paddingVertical: 10, color: C.text, fontSize: 14, marginBottom: 8 },
  saveBtn: { backgroundColor: C.primary, borderRadius: 8, paddingVertical: 12, alignItems: 'center' },
  saveBtnText: { fontSize: 14, fontWeight: '700', color: '#000' },
  loadBox: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  scroll: { flex: 1 },
  scrollContent: { padding: 16 },
  summaryBox: { backgroundColor: C.surfEl, borderWidth: 1, borderColor: C.hudBorder, borderRadius: 12, padding: 16, marginBottom: 16, alignItems: 'center' },
  summaryTotal: { fontSize: 32, fontWeight: '900', color: C.primary },
  summaryLabel: { fontSize: 10, color: C.textTer, letterSpacing: 2, marginTop: 4 },
  catBreakdown: { width: '100%', marginTop: 12, borderTopWidth: 1, borderTopColor: C.border, paddingTop: 12 },
  catRow: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 4 },
  catName: { fontSize: 12, color: C.textSec, textTransform: 'capitalize' },
  catAmt: { fontSize: 12, color: C.text, fontWeight: '600' },
  card: { flexDirection: 'row', alignItems: 'center', gap: 12, backgroundColor: C.surfEl, borderWidth: 1, borderColor: C.border, borderRadius: 10, padding: 14, marginBottom: 8 },
  cardTitle: { fontSize: 14, fontWeight: '600', color: C.text },
  completed: { textDecorationLine: 'line-through', color: C.textTer },
  cardSub: { fontSize: 12, color: C.textSec, marginTop: 2, lineHeight: 18 },
  cardMeta: { fontSize: 10, color: C.textTer, marginTop: 4 },
  expAmtBox: { backgroundColor: 'rgba(239,68,68,0.1)', borderRadius: 8, paddingHorizontal: 10, paddingVertical: 6 },
  expAmt: { fontSize: 14, fontWeight: '800', color: C.err },
  emptyBox: { alignItems: 'center', paddingTop: 60 },
  emptyText: { fontSize: 16, fontWeight: '600', color: C.textSec, marginTop: 12 },
  emptyHint: { fontSize: 12, color: C.textTer, marginTop: 6, textAlign: 'center' },
});
