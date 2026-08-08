import React, { useState, useCallback } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, FlatList, StyleSheet,
  ActivityIndicator, Alert,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useFocusEffect } from 'expo-router';

const API_URL = process.env.EXPO_PUBLIC_BACKEND_URL;

const COLORS = {
  background: '#050505',
  surface: '#121212',
  surfaceElevated: '#1A1A18',
  primary: '#FFB800',
  primaryGlow: 'rgba(255, 184, 0, 0.4)',
  textPrimary: '#FFFFFF',
  textSecondary: '#A1A1AA',
  textTertiary: '#71717A',
  border: '#27272A',
  borderHud: 'rgba(255, 184, 0, 0.2)',
  error: '#EF4444',
};

interface Conversation {
  id: string;
  title: string;
  created_at: string;
  updated_at: string;
  message_count: number;
  last_message?: string;
}

export default function HistoryScreen() {
  const router = useRouter();
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isSearching, setIsSearching] = useState(false);

  useFocusEffect(
    useCallback(() => {
      loadConversations();
    }, [])
  );

  const loadConversations = async () => {
    setIsLoading(true);
    try {
      const res = await fetch(`${API_URL}/api/conversations`);
      const data = await res.json();
      setConversations(data.conversations || []);
    } catch (err) {
      console.error('Failed to load conversations:', err);
    } finally {
      setIsLoading(false);
    }
  };

  const searchConversations = async (query: string) => {
    if (!query.trim()) {
      loadConversations();
      return;
    }
    setIsSearching(true);
    try {
      const res = await fetch(`${API_URL}/api/conversations/search?q=${encodeURIComponent(query)}`);
      const data = await res.json();
      setConversations(data.conversations || []);
    } catch (err) {
      console.error('Search failed:', err);
    } finally {
      setIsSearching(false);
    }
  };

  const deleteConversation = (id: string) => {
    Alert.alert(
      'Delete Conversation',
      'Shall I purge this from the archives, Sir?',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: async () => {
            try {
              await fetch(`${API_URL}/api/conversations/${id}`, { method: 'DELETE' });
              setConversations(prev => prev.filter(c => c.id !== id));
            } catch (err) {
              console.error('Delete failed:', err);
            }
          },
        },
      ]
    );
  };

  const clearAll = () => {
    Alert.alert(
      'Clear All History',
      'This will erase all conversation records. Proceed?',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Clear All',
          style: 'destructive',
          onPress: async () => {
            try {
              await fetch(`${API_URL}/api/conversations`, { method: 'DELETE' });
              setConversations([]);
            } catch (err) {
              console.error('Clear all failed:', err);
            }
          },
        },
      ]
    );
  };

  const openConversation = (conv: Conversation) => {
    router.push({ pathname: '/', params: { conversationId: conv.id } });
  };

  const formatDate = (dateStr: string) => {
    const date = new Date(dateStr);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

    if (diffDays === 0) return 'Today';
    if (diffDays === 1) return 'Yesterday';
    if (diffDays < 7) return `${diffDays}d ago`;
    return date.toLocaleDateString();
  };

  const renderConversation = ({ item }: { item: Conversation }) => (
    <TouchableOpacity
      testID={`conversation-item-${item.id}`}
      style={styles.conversationCard}
      onPress={() => openConversation(item)}
      onLongPress={() => deleteConversation(item.id)}
    >
      <View style={styles.cardLeft}>
        <View style={styles.cardIcon}>
          <Ionicons name="chatbubble" size={16} color={COLORS.primary} />
        </View>
        <View style={styles.cardContent}>
          <Text style={styles.cardTitle} numberOfLines={1}>{item.title}</Text>
          {item.last_message && (
            <Text style={styles.cardPreview} numberOfLines={1}>{item.last_message}</Text>
          )}
          <View style={styles.cardMeta}>
            <Text style={styles.cardDate}>{formatDate(item.updated_at)}</Text>
            <View style={styles.cardBadge}>
              <Text style={styles.cardBadgeText}>{item.message_count} msgs</Text>
            </View>
          </View>
        </View>
      </View>
      <Ionicons name="chevron-forward" size={18} color={COLORS.textTertiary} />
    </TouchableOpacity>
  );

  const renderEmpty = () => (
    <View style={styles.emptyContainer}>
      <Ionicons name="file-tray-outline" size={48} color={COLORS.textTertiary} />
      <Text style={styles.emptyTitle}>No Conversations Yet</Text>
      <Text style={styles.emptySubtitle}>
        {searchQuery ? 'No results found. Try a different search.' : 'Start chatting with JARVIS to see your history here.'}
      </Text>
    </View>
  );

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* Header */}
      <View testID="history-header" style={styles.header}>
        <View>
          <Text style={styles.headerTitle}>MISSION LOG</Text>
          <Text style={styles.headerSubtitle}>
            {conversations.length} CONVERSATION{conversations.length !== 1 ? 'S' : ''} ARCHIVED
          </Text>
        </View>
        {conversations.length > 0 && (
          <TouchableOpacity testID="clear-all-btn" onPress={clearAll} style={styles.clearBtn}>
            <Ionicons name="trash-outline" size={18} color={COLORS.error} />
          </TouchableOpacity>
        )}
      </View>

      {/* Search */}
      <View style={styles.searchContainer}>
        <Ionicons name="search" size={18} color={COLORS.textTertiary} style={styles.searchIcon} />
        <TextInput
          testID="search-input"
          style={styles.searchInput}
          placeholder="Search archives..."
          placeholderTextColor={COLORS.textTertiary}
          value={searchQuery}
          onChangeText={(text) => {
            setSearchQuery(text);
            searchConversations(text);
          }}
        />
        {isSearching && <ActivityIndicator size="small" color={COLORS.primary} />}
        {searchQuery.length > 0 && (
          <TouchableOpacity testID="clear-search-btn" onPress={() => { setSearchQuery(''); loadConversations(); }}>
            <Ionicons name="close-circle" size={18} color={COLORS.textTertiary} />
          </TouchableOpacity>
        )}
      </View>

      {/* List */}
      {isLoading ? (
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color={COLORS.primary} />
          <Text style={styles.loadingText}>ACCESSING ARCHIVES...</Text>
        </View>
      ) : (
        <FlatList
          testID="conversations-list"
          data={conversations}
          renderItem={renderConversation}
          keyExtractor={(item) => item.id}
          contentContainerStyle={[
            styles.listContent,
            conversations.length === 0 && styles.emptyList,
          ]}
          ListEmptyComponent={renderEmpty}
          showsVerticalScrollIndicator={false}
          onRefresh={loadConversations}
          refreshing={isLoading}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: COLORS.background,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
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
  clearBtn: {
    padding: 10,
    borderWidth: 1,
    borderColor: 'rgba(239, 68, 68, 0.3)',
    borderRadius: 8,
  },
  searchContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    margin: 16,
    paddingHorizontal: 14,
    backgroundColor: COLORS.surfaceElevated,
    borderWidth: 1,
    borderColor: COLORS.border,
    borderRadius: 12,
    height: 48,
  },
  searchIcon: {
    marginRight: 10,
  },
  searchInput: {
    flex: 1,
    color: COLORS.textPrimary,
    fontSize: 14,
  },
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  loadingText: {
    marginTop: 12,
    fontSize: 11,
    color: COLORS.textTertiary,
    letterSpacing: 2,
  },
  listContent: {
    paddingHorizontal: 16,
    paddingBottom: 24,
  },
  emptyList: {
    flex: 1,
    justifyContent: 'center',
  },
  conversationCard: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: COLORS.surfaceElevated,
    borderWidth: 1,
    borderColor: COLORS.border,
    borderRadius: 12,
    padding: 16,
    marginBottom: 10,
  },
  cardLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  cardIcon: {
    width: 36,
    height: 36,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: COLORS.borderHud,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255, 184, 0, 0.08)',
    marginRight: 12,
  },
  cardContent: {
    flex: 1,
  },
  cardTitle: {
    fontSize: 15,
    fontWeight: '600',
    color: COLORS.textPrimary,
    marginBottom: 2,
  },
  cardPreview: {
    fontSize: 13,
    color: COLORS.textSecondary,
    marginBottom: 4,
  },
  cardMeta: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  cardDate: {
    fontSize: 11,
    color: COLORS.textTertiary,
  },
  cardBadge: {
    backgroundColor: 'rgba(255, 184, 0, 0.1)',
    borderRadius: 4,
    paddingHorizontal: 6,
    paddingVertical: 2,
  },
  cardBadgeText: {
    fontSize: 10,
    color: COLORS.primary,
    fontWeight: '600',
  },
  emptyContainer: {
    alignItems: 'center',
    paddingHorizontal: 40,
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: COLORS.textSecondary,
    marginTop: 16,
    marginBottom: 8,
  },
  emptySubtitle: {
    fontSize: 14,
    color: COLORS.textTertiary,
    textAlign: 'center',
    lineHeight: 20,
  },
});
