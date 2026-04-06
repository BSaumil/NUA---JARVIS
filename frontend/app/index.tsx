import React, { useState, useRef, useEffect, useCallback } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, FlatList, StyleSheet,
  KeyboardAvoidingView, Platform, Animated, ActivityIndicator, Keyboard,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { Audio } from 'expo-av';
import { useLocalSearchParams } from 'expo-router';

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
  error: '#EF4444',
  success: '#10B981',
};

interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  created_at: string;
}

function TypingIndicator() {
  const dot1 = useRef(new Animated.Value(0)).current;
  const dot2 = useRef(new Animated.Value(0)).current;
  const dot3 = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    const animate = (dot: Animated.Value, delay: number) => {
      return Animated.loop(
        Animated.sequence([
          Animated.delay(delay),
          Animated.timing(dot, { toValue: 1, duration: 400, useNativeDriver: true }),
          Animated.timing(dot, { toValue: 0, duration: 400, useNativeDriver: true }),
        ])
      );
    };
    const a1 = animate(dot1, 0);
    const a2 = animate(dot2, 200);
    const a3 = animate(dot3, 400);
    a1.start(); a2.start(); a3.start();
    return () => { a1.stop(); a2.stop(); a3.stop(); };
  }, []);

  const dotStyle = (anim: Animated.Value) => ({
    opacity: anim.interpolate({ inputRange: [0, 1], outputRange: [0.3, 1] }),
    transform: [{ scale: anim.interpolate({ inputRange: [0, 1], outputRange: [0.8, 1.2] }) }],
  });

  return (
    <View testID="typing-indicator" style={styles.typingContainer}>
      <View style={styles.aiBubble}>
        <View style={styles.typingDots}>
          {[dot1, dot2, dot3].map((d, i) => (
            <Animated.View key={i} style={[styles.typingDot, dotStyle(d)]} />
          ))}
        </View>
      </View>
    </View>
  );
}

function ChatBubble({ message, onPlayAudio }: { message: Message; onPlayAudio: (text: string) => void }) {
  const isUser = message.role === 'user';
  const fadeAnim = useRef(new Animated.Value(0)).current;
  const slideAnim = useRef(new Animated.Value(10)).current;

  useEffect(() => {
    Animated.parallel([
      Animated.timing(fadeAnim, { toValue: 1, duration: 300, useNativeDriver: true }),
      Animated.timing(slideAnim, { toValue: 0, duration: 300, useNativeDriver: true }),
    ]).start();
  }, []);

  return (
    <Animated.View
      testID={`chat-bubble-${message.id}`}
      style={[
        styles.bubbleWrapper,
        isUser ? styles.userBubbleWrapper : styles.aiBubbleWrapper,
        { opacity: fadeAnim, transform: [{ translateY: slideAnim }] },
      ]}
    >
      {!isUser && (
        <View style={styles.avatarContainer}>
          <View style={styles.avatarRing}>
            <Ionicons name="hardware-chip" size={16} color={COLORS.primary} />
          </View>
        </View>
      )}
      <View style={[isUser ? styles.userBubble : styles.aiBubble, { flex: 1 }]}>
        <Text style={[styles.bubbleText, isUser && styles.userBubbleText]}>
          {message.content}
        </Text>
        <View style={styles.bubbleFooter}>
          <Text style={styles.timestamp}>
            {new Date(message.created_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
          </Text>
          {!isUser && (
            <TouchableOpacity
              testID={`play-audio-${message.id}`}
              onPress={() => onPlayAudio(message.content)}
              style={styles.audioBtn}
            >
              <Ionicons name="volume-high" size={14} color={COLORS.primary} />
            </TouchableOpacity>
          )}
        </View>
      </View>
    </Animated.View>
  );
}

function VoiceWaveform({ isRecording }: { isRecording: boolean }) {
  const bars = useRef(Array.from({ length: 12 }, () => new Animated.Value(0.3))).current;

  useEffect(() => {
    if (!isRecording) return;
    const animations = bars.map((bar) => {
      return Animated.loop(
        Animated.sequence([
          Animated.timing(bar, { toValue: Math.random() * 0.7 + 0.3, duration: 150 + Math.random() * 200, useNativeDriver: true }),
          Animated.timing(bar, { toValue: Math.random() * 0.3 + 0.1, duration: 150 + Math.random() * 200, useNativeDriver: true }),
        ])
      );
    });
    animations.forEach(a => a.start());
    return () => animations.forEach(a => a.stop());
  }, [isRecording]);

  if (!isRecording) return null;

  return (
    <View testID="voice-waveform" style={styles.waveformContainer}>
      {bars.map((bar, i) => (
        <Animated.View
          key={i}
          style={[
            styles.waveformBar,
            { transform: [{ scaleY: bar }] },
          ]}
        />
      ))}
    </View>
  );
}

export default function ChatScreen() {
  const params = useLocalSearchParams<{ conversationId?: string }>();
  const [messages, setMessages] = useState<Message[]>([]);
  const [inputText, setInputText] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isRecording, setIsRecording] = useState(false);
  const [isSpeaking, setIsSpeaking] = useState(false);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [recording, setRecording] = useState<Audio.Recording | null>(null);
  const flatListRef = useRef<FlatList>(null);
  const soundRef = useRef<Audio.Sound | null>(null);
  const pulseAnim = useRef(new Animated.Value(1)).current;

  // Load conversation if ID provided
  useEffect(() => {
    if (params.conversationId) {
      loadConversation(params.conversationId);
    }
  }, [params.conversationId]);

  // Pulse animation for recording
  useEffect(() => {
    if (isRecording) {
      Animated.loop(
        Animated.sequence([
          Animated.timing(pulseAnim, { toValue: 1.3, duration: 600, useNativeDriver: true }),
          Animated.timing(pulseAnim, { toValue: 1, duration: 600, useNativeDriver: true }),
        ])
      ).start();
    } else {
      pulseAnim.setValue(1);
    }
  }, [isRecording]);

  const loadConversation = async (convId: string) => {
    try {
      const res = await fetch(`${API_URL}/api/conversations/${convId}`);
      const data = await res.json();
      if (data.messages) {
        setMessages(data.messages);
        setConversationId(convId);
      }
    } catch (err) {
      console.error('Failed to load conversation:', err);
    }
  };

  const sendMessage = useCallback(async () => {
    const text = inputText.trim();
    if (!text || isLoading) return;

    Keyboard.dismiss();
    setInputText('');
    setIsLoading(true);

    const tempMsg: Message = {
      id: `temp-${Date.now()}`,
      role: 'user',
      content: text,
      created_at: new Date().toISOString(),
    };
    setMessages(prev => [...prev, tempMsg]);

    try {
      const res = await fetch(`${API_URL}/api/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content: text, conversation_id: conversationId }),
      });
      const data = await res.json();

      if (data.error) {
        const errMsg: Message = {
          id: `err-${Date.now()}`,
          role: 'assistant',
          content: `System error: ${data.error}. My circuits seem to be having a moment.`,
          created_at: new Date().toISOString(),
        };
        setMessages(prev => [...prev, errMsg]);
      } else {
        if (!conversationId) setConversationId(data.conversation_id);
        setMessages(prev => [...prev, data.message]);
      }
    } catch (err) {
      const errMsg: Message = {
        id: `err-${Date.now()}`,
        role: 'assistant',
        content: 'Connection lost. Even the best systems need a moment. Try again.',
        created_at: new Date().toISOString(),
      };
      setMessages(prev => [...prev, errMsg]);
    } finally {
      setIsLoading(false);
    }
  }, [inputText, isLoading, conversationId]);

  const startRecording = async () => {
    try {
      const { status } = await Audio.requestPermissionsAsync();
      if (status !== 'granted') return;

      await Audio.setAudioModeAsync({
        allowsRecordingIOS: true,
        playsInSilentModeIOS: true,
      });

      const { recording: rec } = await Audio.Recording.createAsync(
        Audio.RecordingOptionsPresets.HIGH_QUALITY
      );
      setRecording(rec);
      setIsRecording(true);
    } catch (err) {
      console.error('Failed to start recording:', err);
    }
  };

  const stopRecording = async () => {
    if (!recording) return;
    setIsRecording(false);
    setIsLoading(true);

    try {
      await recording.stopAndUnloadAsync();
      const uri = recording.getURI();
      setRecording(null);

      if (!uri) return;

      const formData = new FormData();
      formData.append('audio', {
        uri,
        type: 'audio/m4a',
        name: 'recording.m4a',
      } as any);
      if (conversationId) formData.append('conversation_id', conversationId);

      const res = await fetch(`${API_URL}/api/chat/voice`, {
        method: 'POST',
        body: formData,
      });
      const data = await res.json();

      if (data.error) {
        const errMsg: Message = {
          id: `err-${Date.now()}`,
          role: 'assistant',
          content: `Voice processing issue: ${data.error}`,
          created_at: new Date().toISOString(),
        };
        setMessages(prev => [...prev, errMsg]);
      } else {
        if (!conversationId) setConversationId(data.conversation_id);
        // Add the transcribed user message
        const userMsg: Message = {
          id: `voice-${Date.now()}`,
          role: 'user',
          content: data.transcribed_text,
          created_at: new Date().toISOString(),
        };
        setMessages(prev => [...prev, userMsg, data.message]);
      }
    } catch (err) {
      console.error('Voice chat error:', err);
    } finally {
      setIsLoading(false);
    }
  };

  const playAudio = async (text: string) => {
    if (isSpeaking) {
      if (soundRef.current) {
        await soundRef.current.stopAsync();
        await soundRef.current.unloadAsync();
        soundRef.current = null;
      }
      setIsSpeaking(false);
      return;
    }

    setIsSpeaking(true);
    try {
      const res = await fetch(`${API_URL}/api/tts`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text, voice: 'onyx' }),
      });
      const data = await res.json();

      if (data.audio_base64) {
        await Audio.setAudioModeAsync({ playsInSilentModeIOS: true });
        const { sound } = await Audio.Sound.createAsync(
          { uri: `data:audio/mp3;base64,${data.audio_base64}` }
        );
        soundRef.current = sound;
        sound.setOnPlaybackStatusUpdate((status) => {
          if (status.isLoaded && status.didJustFinish) {
            setIsSpeaking(false);
            sound.unloadAsync();
            soundRef.current = null;
          }
        });
        await sound.playAsync();
      }
    } catch (err) {
      console.error('TTS error:', err);
      setIsSpeaking(false);
    }
  };

  const newConversation = () => {
    setMessages([]);
    setConversationId(null);
  };

  const renderMessage = ({ item }: { item: Message }) => (
    <ChatBubble message={item} onPlayAudio={playAudio} />
  );

  const renderEmpty = () => (
    <View style={styles.emptyContainer}>
      <View style={styles.jarvisLogo}>
        <View style={styles.jarvisLogoInner}>
          <Ionicons name="hardware-chip" size={40} color={COLORS.primary} />
        </View>
      </View>
      <Text style={styles.emptyTitle}>J.A.R.V.I.S.</Text>
      <Text style={styles.emptySubtitle}>Just A Rather Very Intelligent System</Text>
      <Text style={styles.emptyHint}>Type a message or hold the mic to speak</Text>
      <View style={styles.suggestionsContainer}>
        {['What can you do?', 'Tell me a joke', 'Motivate me'].map((s, i) => (
          <TouchableOpacity
            key={i}
            testID={`suggestion-${i}`}
            style={styles.suggestionChip}
            onPress={() => { setInputText(s); }}
          >
            <Text style={styles.suggestionText}>{s}</Text>
          </TouchableOpacity>
        ))}
      </View>
    </View>
  );

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* Header */}
      <View testID="chat-header" style={styles.header}>
        <View style={styles.headerLeft}>
          <View style={styles.statusDot} />
          <View>
            <Text style={styles.headerTitle}>J.A.R.V.I.S.</Text>
            <Text style={styles.headerSubtitle}>
              {isLoading ? 'PROCESSING...' : isSpeaking ? 'SPEAKING...' : 'ONLINE'}
            </Text>
          </View>
        </View>
        <TouchableOpacity testID="new-conversation-btn" onPress={newConversation} style={styles.headerBtn}>
          <Ionicons name="add-circle-outline" size={24} color={COLORS.primary} />
        </TouchableOpacity>
      </View>

      {/* Chat Messages */}
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={styles.chatArea}
        keyboardVerticalOffset={100}
      >
        <FlatList
          ref={flatListRef}
          testID="messages-list"
          data={messages}
          renderItem={renderMessage}
          keyExtractor={(item) => item.id}
          contentContainerStyle={[
            styles.messagesList,
            messages.length === 0 && styles.emptyList,
          ]}
          ListEmptyComponent={renderEmpty}
          ListFooterComponent={isLoading ? <TypingIndicator /> : null}
          onContentSizeChange={() => {
            if (messages.length > 0) {
              flatListRef.current?.scrollToEnd({ animated: true });
            }
          }}
          showsVerticalScrollIndicator={false}
        />

        {/* Voice Waveform */}
        <VoiceWaveform isRecording={isRecording} />

        {/* Input Area */}
        <View testID="input-area" style={styles.inputContainer}>
          <View style={styles.inputRow}>
            <TextInput
              testID="message-input"
              style={styles.textInput}
              placeholder="Command JARVIS..."
              placeholderTextColor={COLORS.textTertiary}
              value={inputText}
              onChangeText={setInputText}
              onSubmitEditing={sendMessage}
              returnKeyType="send"
              multiline
              maxLength={2000}
            />
            {inputText.trim() ? (
              <TouchableOpacity
                testID="send-button"
                onPress={sendMessage}
                style={styles.sendBtn}
                disabled={isLoading}
              >
                {isLoading ? (
                  <ActivityIndicator size="small" color={COLORS.background} />
                ) : (
                  <Ionicons name="send" size={20} color={COLORS.background} />
                )}
              </TouchableOpacity>
            ) : (
              <Animated.View style={{ transform: [{ scale: pulseAnim }] }}>
                <TouchableOpacity
                  testID="mic-button"
                  onPressIn={startRecording}
                  onPressOut={stopRecording}
                  style={[styles.micBtn, isRecording && styles.micBtnActive]}
                >
                  <Ionicons
                    name={isRecording ? 'radio' : 'mic'}
                    size={22}
                    color={isRecording ? COLORS.background : COLORS.primary}
                  />
                </TouchableOpacity>
              </Animated.View>
            )}
          </View>
        </View>
      </KeyboardAvoidingView>
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
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: COLORS.border,
    backgroundColor: COLORS.surface,
  },
  headerLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  statusDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
    backgroundColor: COLORS.success,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: COLORS.primary,
    letterSpacing: 2,
  },
  headerSubtitle: {
    fontSize: 10,
    fontWeight: '600',
    color: COLORS.textTertiary,
    letterSpacing: 2,
  },
  headerBtn: {
    padding: 8,
  },
  chatArea: {
    flex: 1,
  },
  messagesList: {
    paddingHorizontal: 16,
    paddingVertical: 16,
  },
  emptyList: {
    flex: 1,
    justifyContent: 'center',
  },
  bubbleWrapper: {
    flexDirection: 'row',
    marginBottom: 16,
    maxWidth: '85%',
  },
  userBubbleWrapper: {
    alignSelf: 'flex-end',
  },
  aiBubbleWrapper: {
    alignSelf: 'flex-start',
  },
  userBubble: {
    backgroundColor: COLORS.surfaceElevated,
    borderWidth: 1,
    borderColor: COLORS.border,
    borderRadius: 16,
    borderBottomRightRadius: 4,
    padding: 14,
  },
  aiBubble: {
    backgroundColor: 'rgba(255, 184, 0, 0.05)',
    borderWidth: 1,
    borderColor: COLORS.borderHud,
    borderRadius: 16,
    borderBottomLeftRadius: 4,
    padding: 14,
  },
  avatarContainer: {
    marginRight: 8,
    alignSelf: 'flex-end',
  },
  avatarRing: {
    width: 32,
    height: 32,
    borderRadius: 16,
    borderWidth: 1.5,
    borderColor: COLORS.primary,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255, 184, 0, 0.1)',
  },
  bubbleText: {
    fontSize: 15,
    lineHeight: 22,
    color: COLORS.textPrimary,
  },
  userBubbleText: {
    color: COLORS.textPrimary,
  },
  bubbleFooter: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 6,
  },
  timestamp: {
    fontSize: 10,
    color: COLORS.textTertiary,
    letterSpacing: 0.5,
  },
  audioBtn: {
    padding: 4,
    marginLeft: 8,
  },
  typingContainer: {
    paddingHorizontal: 16,
    paddingBottom: 8,
    alignSelf: 'flex-start',
  },
  typingDots: {
    flexDirection: 'row',
    gap: 6,
    paddingVertical: 4,
  },
  typingDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: COLORS.primary,
  },
  waveformContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    height: 48,
    paddingHorizontal: 24,
    gap: 4,
    backgroundColor: 'rgba(255, 184, 0, 0.05)',
    borderTopWidth: 1,
    borderTopColor: COLORS.borderHud,
  },
  waveformBar: {
    width: 4,
    height: 32,
    borderRadius: 2,
    backgroundColor: COLORS.primary,
  },
  inputContainer: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderTopWidth: 1,
    borderTopColor: COLORS.border,
    backgroundColor: COLORS.surface,
  },
  inputRow: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 10,
  },
  textInput: {
    flex: 1,
    backgroundColor: COLORS.surfaceElevated,
    borderWidth: 1,
    borderColor: COLORS.border,
    borderRadius: 24,
    paddingHorizontal: 18,
    paddingVertical: 12,
    color: COLORS.textPrimary,
    fontSize: 15,
    maxHeight: 100,
  },
  sendBtn: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: COLORS.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  micBtn: {
    width: 44,
    height: 44,
    borderRadius: 22,
    borderWidth: 1.5,
    borderColor: COLORS.primary,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255, 184, 0, 0.1)',
  },
  micBtnActive: {
    backgroundColor: COLORS.primary,
    borderColor: COLORS.primary,
  },
  emptyContainer: {
    alignItems: 'center',
    paddingHorizontal: 32,
  },
  jarvisLogo: {
    width: 96,
    height: 96,
    borderRadius: 48,
    borderWidth: 2,
    borderColor: COLORS.primary,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255, 184, 0, 0.08)',
    marginBottom: 20,
  },
  jarvisLogoInner: {
    width: 64,
    height: 64,
    borderRadius: 32,
    borderWidth: 1,
    borderColor: COLORS.borderHud,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyTitle: {
    fontSize: 28,
    fontWeight: '800',
    color: COLORS.primary,
    letterSpacing: 4,
    marginBottom: 4,
  },
  emptySubtitle: {
    fontSize: 11,
    color: COLORS.textTertiary,
    letterSpacing: 1.5,
    marginBottom: 24,
    textTransform: 'uppercase',
  },
  emptyHint: {
    fontSize: 14,
    color: COLORS.textSecondary,
    marginBottom: 24,
  },
  suggestionsContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'center',
    gap: 8,
  },
  suggestionChip: {
    borderWidth: 1,
    borderColor: COLORS.borderHud,
    borderRadius: 20,
    paddingHorizontal: 16,
    paddingVertical: 10,
    backgroundColor: 'rgba(255, 184, 0, 0.05)',
  },
  suggestionText: {
    fontSize: 13,
    color: COLORS.primary,
    fontWeight: '500',
  },
});
