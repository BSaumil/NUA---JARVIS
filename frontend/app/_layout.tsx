import { Tabs } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { StyleSheet, View, StatusBar } from 'react-native';

const C = {
  bg: '#050505', surface: '#0F0F0F', primary: '#FFB800',
  textSec: '#A1A1AA', border: '#1E1E1E',
};

function ChatIcon({ color, size }: { color: string; size: number }) {
  return <Ionicons name="chatbubble-ellipses" size={size} color={color} />;
}
function DashIcon({ color, size }: { color: string; size: number }) {
  return <Ionicons name="grid" size={size} color={color} />;
}
function MemIcon({ color, size }: { color: string; size: number }) {
  return <Ionicons name="bulb" size={size} color={color} />;
}
function LifeIcon({ color, size }: { color: string; size: number }) {
  return <Ionicons name="briefcase" size={size} color={color} />;
}

export default function RootLayout() {
  return (
    <View style={styles.container}>
      <StatusBar barStyle="light-content" backgroundColor={C.bg} />
      <Tabs
        screenOptions={{
          headerShown: false,
          tabBarStyle: styles.tabBar,
          tabBarActiveTintColor: C.primary,
          tabBarInactiveTintColor: C.textSec,
          tabBarLabelStyle: styles.tabLabel,
        }}
      >
        <Tabs.Screen name="index" options={{ title: 'Nua', tabBarIcon: ChatIcon }} />
        <Tabs.Screen name="dashboard" options={{ title: 'Dashboard', tabBarIcon: DashIcon }} />
        <Tabs.Screen name="memory" options={{ title: 'Memory', tabBarIcon: MemIcon }} />
        <Tabs.Screen name="life" options={{ title: 'Life Hub', tabBarIcon: LifeIcon }} />
        <Tabs.Screen name="history" options={{ href: null }} />
        <Tabs.Screen name="actions" options={{ href: null }} />
      </Tabs>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: C.bg },
  tabBar: {
    backgroundColor: C.surface, borderTopColor: C.border,
    borderTopWidth: 1, height: 64, paddingBottom: 8, paddingTop: 8,
  },
  tabLabel: { fontSize: 9, fontWeight: '700', letterSpacing: 0.8, textTransform: 'uppercase' },
});
