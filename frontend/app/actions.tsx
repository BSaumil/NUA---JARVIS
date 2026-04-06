import React, { useState, useRef, useEffect } from 'react';
import {
  View, Text, TouchableOpacity, StyleSheet, ScrollView,
  ActivityIndicator, Animated,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';

const API_URL = process.env.EXPO_PUBLIC_BACKEND_URL;

const COLORS = {
  background: '#050505',
  surface: '#121212',
  surfaceElevated: '#1A1A18',
  primary: '#FFB800',
  primaryGlow: 'rgba(255, 184, 0, 0.4)',
  secondary: '#FF4500',
  accent: '#00FFCC',
  textPrimary: '#FFFFFF',
  textSecondary: '#A1A1AA',
  textTertiary: '#71717A',
  border: '#27272A',
  borderHud: 'rgba(255, 184, 0, 0.2)',
};

interface QuickAction {
  id: string;
  icon: string;
  title: string;
  description: string;
  action: string;
  color: string;
}

const QUICK_ACTIONS: QuickAction[] = [
  {
    id: '1', icon: 'happy', title: 'Tell a Joke',
    description: 'Need a laugh? JARVIS delivers.',
    action: 'joke', color: COLORS.primary,
  },
  {
    id: '2', icon: 'flask', title: 'Fun Fact',
    description: 'Mind-blowing science & tech.',
    action: 'fact', color: COLORS.accent,
  },
  {
    id: '3', icon: 'rocket', title: 'Motivate Me',
    description: 'Channel your inner Stark.',
    action: 'motivation', color: COLORS.secondary,
  },
  {
    id: '4', icon: 'code-slash', title: 'Code Help',
    description: 'Quick coding snippets.',
    action: 'code', color: '#3B82F6',
  },
  {
    id: '5', icon: 'help-circle', title: 'Trivia',
    description: 'Test your knowledge.',
    action: 'trivia', color: '#A855F7',
  },
  {
    id: '6', icon: 'calculator', title: 'Calculate',
    description: 'Math powered by AI.',
    action: 'calculate', color: '#10B981',
  },
  {
    id: '7', icon: 'cloudy', title: 'Weather',
    description: 'Coming soon...',
    action: 'weather', color: '#06B6D4',
  },
  {
    id: '8', icon: 'newspaper', title: 'News Brief',
    description: 'Coming soon...',
    action: 'news', color: '#F43F5E',
  },
];

function ActionCard({ action, onPress, index }: { action: QuickAction; onPress: () => void; index: number }) {
  const fadeAnim = useRef(new Animated.Value(0)).current;
  const slideAnim = useRef(new Animated.Value(20)).current;

  useEffect(() => {
    Animated.parallel([
      Animated.timing(fadeAnim, { toValue: 1, duration: 400, delay: index * 80, useNativeDriver: true }),
      Animated.timing(slideAnim, { toValue: 0, duration: 400, delay: index * 80, useNativeDriver: true }),
    ]).start();
  }, []);

  return (
    <Animated.View style={[{ opacity: fadeAnim, transform: [{ translateY: slideAnim }] }]}>
      <TouchableOpacity
        testID={`action-${action.action}`}
        style={styles.actionCard}
        onPress={onPress}
        activeOpacity={0.7}
      >
        <View style={[styles.actionIconContainer, { borderColor: action.color + '40' }]}>
          <Ionicons name={action.icon as any} size={24} color={action.color} />
        </View>
        <Text style={styles.actionTitle}>{action.title}</Text>
        <Text style={styles.actionDescription}>{action.description}</Text>
      </TouchableOpacity>
    </Animated.View>
  );
}

export default function ActionsScreen() {
  const [response, setResponse] = useState<string | null>(null);
  const [activeAction, setActiveAction] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const fadeResponse = useRef(new Animated.Value(0)).current;

  const executeAction = async (action: QuickAction) => {
    setIsLoading(true);
    setActiveAction(action.action);
    setResponse(null);

    try {
      const res = await fetch(`${API_URL}/api/quick-action`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: action.action }),
      });
      const data = await res.json();
      setResponse(data.response || data.error || 'No response received.');
      fadeResponse.setValue(0);
      Animated.timing(fadeResponse, { toValue: 1, duration: 400, useNativeDriver: true }).start();
    } catch (err) {
      setResponse('Connection issue. Even JARVIS needs a stable link.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* Header */}
      <View testID="actions-header" style={styles.header}>
        <Text style={styles.headerTitle}>QUICK ACTIONS</Text>
        <Text style={styles.headerSubtitle}>INSTANT ACCESS TO JARVIS CAPABILITIES</Text>
      </View>

      <ScrollView
        style={styles.scrollView}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        {/* Response Area */}
        {(isLoading || response) && (
          <View testID="action-response" style={styles.responseContainer}>
            <View style={styles.responseHeader}>
              <Ionicons name="hardware-chip" size={16} color={COLORS.primary} />
              <Text style={styles.responseLabel}>
                {activeAction ? activeAction.toUpperCase() : 'RESPONSE'}
              </Text>
            </View>
            {isLoading ? (
              <View style={styles.responseLoading}>
                <ActivityIndicator size="small" color={COLORS.primary} />
                <Text style={styles.responseLoadingText}>JARVIS is thinking...</Text>
              </View>
            ) : (
              <Animated.View style={{ opacity: fadeResponse }}>
                <Text testID="action-response-text" style={styles.responseText}>{response}</Text>
              </Animated.View>
            )}
          </View>
        )}

        {/* Actions Grid */}
        <View style={styles.grid}>
          {QUICK_ACTIONS.map((action, index) => (
            <ActionCard
              key={action.id}
              action={action}
              index={index}
              onPress={() => executeAction(action)}
            />
          ))}
        </View>

        {/* Upcoming Features */}
        <View style={styles.upcomingSection}>
          <Text style={styles.upcomingTitle}>UPCOMING CAPABILITIES</Text>
          <View style={styles.upcomingList}>
            {[
              'Smart Home Control',
              'Calendar Integration',
              'Email Briefing',
              'Stock Market Tracker',
              'Flight Status Monitor',
              'Image Recognition',
            ].map((feature, i) => (
              <View key={i} style={styles.upcomingItem}>
                <View style={styles.upcomingDot} />
                <Text style={styles.upcomingText}>{feature}</Text>
              </View>
            ))}
          </View>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: COLORS.background,
  },
  header: {
    paddingHorizontal: 20,
    paddingVertical: 16,
    borderBottomWidth: 1,
    borderBottomColor: COLORS.border,
    backgroundColor: COLORS.surface,
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: COLORS.primary,
    letterSpacing: 3,
  },
  headerSubtitle: {
    fontSize: 10,
    color: COLORS.textTertiary,
    letterSpacing: 1.5,
    marginTop: 2,
  },
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    padding: 16,
    paddingBottom: 32,
  },
  responseContainer: {
    backgroundColor: COLORS.surfaceElevated,
    borderWidth: 1,
    borderColor: COLORS.borderHud,
    borderRadius: 12,
    padding: 16,
    marginBottom: 20,
  },
  responseHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 12,
  },
  responseLabel: {
    fontSize: 10,
    fontWeight: '700',
    color: COLORS.primary,
    letterSpacing: 2,
  },
  responseLoading: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  responseLoadingText: {
    fontSize: 13,
    color: COLORS.textSecondary,
    fontStyle: 'italic',
  },
  responseText: {
    fontSize: 15,
    lineHeight: 24,
    color: COLORS.textPrimary,
  },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
  },
  actionCard: {
    width: '47%',
    backgroundColor: COLORS.surfaceElevated,
    borderWidth: 1,
    borderColor: COLORS.border,
    borderRadius: 12,
    padding: 16,
    minWidth: 155,
  },
  actionIconContainer: {
    width: 44,
    height: 44,
    borderRadius: 12,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.03)',
    marginBottom: 12,
  },
  actionTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: COLORS.textPrimary,
    marginBottom: 4,
  },
  actionDescription: {
    fontSize: 11,
    color: COLORS.textTertiary,
    lineHeight: 16,
  },
  upcomingSection: {
    marginTop: 28,
    padding: 16,
    borderWidth: 1,
    borderColor: COLORS.border,
    borderRadius: 12,
    backgroundColor: COLORS.surface,
  },
  upcomingTitle: {
    fontSize: 12,
    fontWeight: '700',
    color: COLORS.textTertiary,
    letterSpacing: 2,
    marginBottom: 16,
  },
  upcomingList: {
    gap: 12,
  },
  upcomingItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  upcomingDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: COLORS.primaryGlow,
  },
  upcomingText: {
    fontSize: 13,
    color: COLORS.textSecondary,
  },
});
