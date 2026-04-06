import { Tabs } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { StyleSheet, View, StatusBar } from 'react-native';

const COLORS = {
  background: '#050505',
  surface: '#121212',
  primary: '#FFB800',
  primaryGlow: 'rgba(255, 184, 0, 0.4)',
  textSecondary: '#A1A1AA',
  border: '#27272A',
};

function ChatIcon({ color, size }: { color: string; size: number }) {
  return <Ionicons name="chatbubble-ellipses" size={size} color={color} />;
}

function HistoryIcon({ color, size }: { color: string; size: number }) {
  return <Ionicons name="time" size={size} color={color} />;
}

function ActionsIcon({ color, size }: { color: string; size: number }) {
  return <Ionicons name="flash" size={size} color={color} />;
}

export default function RootLayout() {
  return (
    <View style={styles.container}>
      <StatusBar barStyle="light-content" backgroundColor={COLORS.background} />
      <Tabs
        screenOptions={{
          headerShown: false,
          tabBarStyle: styles.tabBar,
          tabBarActiveTintColor: COLORS.primary,
          tabBarInactiveTintColor: COLORS.textSecondary,
          tabBarLabelStyle: styles.tabLabel,
        }}
      >
        <Tabs.Screen
          name="index"
          options={{
            title: 'J.A.R.V.I.S.',
            tabBarIcon: ChatIcon,
          }}
        />
        <Tabs.Screen
          name="history"
          options={{
            title: 'History',
            tabBarIcon: HistoryIcon,
          }}
        />
        <Tabs.Screen
          name="actions"
          options={{
            title: 'Quick Actions',
            tabBarIcon: ActionsIcon,
          }}
        />
      </Tabs>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: COLORS.background,
  },
  tabBar: {
    backgroundColor: COLORS.surface,
    borderTopColor: COLORS.border,
    borderTopWidth: 1,
    height: 64,
    paddingBottom: 8,
    paddingTop: 8,
  },
  tabLabel: {
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 1,
    textTransform: 'uppercase',
  },
});
