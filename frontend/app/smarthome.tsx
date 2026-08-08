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

const DEVICE_ICONS: Record<string, string> = {
  light: 'bulb', thermostat: 'thermometer', speaker: 'volume-high',
  lock: 'lock-closed', camera: 'videocam', plug: 'power', blinds: 'resize',
};

const DEVICE_COLORS: Record<string, string> = {
  light: C.primary, thermostat: C.err, speaker: C.info,
  lock: C.ok, camera: '#A855F7', plug: C.accent, blinds: C.warn,
};

export default function SmartHomeScreen() {
  const router = useRouter();
  const [devices, setDevices] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [controlling, setControlling] = useState<string | null>(null);

  useEffect(() => { load(); }, []);

  const load = async () => {
    try {
      const r = await fetch(`${API}/api/smart-home/devices`);
      const d = await r.json();
      setDevices(d.devices || []);
    } catch (e) { console.error(e); } finally { setLoading(false); }
  };

  const toggleDevice = async (deviceId: string) => {
    setControlling(deviceId);
    try {
      const r = await fetch(`${API}/api/smart-home/control`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ device_id: deviceId, action: 'toggle' }),
      });
      const d = await r.json();
      if (d.device) {
        setDevices(prev => prev.map(dev => dev.id === deviceId ? d.device : dev));
      }
    } catch (e) { console.error(e); } finally { setControlling(null); }
  };

  const activateScene = async (scene: string) => {
    try {
      await fetch(`${API}/api/smart-home/scene`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ scene }),
      });
      load();
    } catch (e) { console.error(e); }
  };

  const isOn = (dev: any) => ['on', 'unlocked', 'open'].includes(dev.status);

  const rooms = [...new Set(devices.map(d => d.room))];

  return (
    <SafeAreaView style={st.container} edges={['top']}>
      <View testID="sh-header" style={st.header}>
        <TouchableOpacity testID="sh-back" onPress={() => router.back()} style={st.backBtn}>
          <Ionicons name="chevron-back" size={22} color={C.primary} />
        </TouchableOpacity>
        <View>
          <Text style={st.hTitle}>SMART HOME</Text>
          <Text style={st.hSub}>SIMULATION MODE</Text>
        </View>
        <View style={st.simBadge}><Text style={st.simText}>DEMO</Text></View>
      </View>

      {loading ? (
        <View style={st.center}><ActivityIndicator size="large" color={C.primary} /></View>
      ) : (
        <ScrollView contentContainerStyle={st.scrollContent} showsVerticalScrollIndicator={false}>
          {/* Scenes */}
          <Text style={st.secTitle}>SCENES</Text>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} style={st.scenesRow}>
            {[
              { id: 'morning', icon: 'sunny', label: 'Morning', color: C.primary },
              { id: 'focus', icon: 'bulb', label: 'Focus', color: C.info },
              { id: 'movie', icon: 'film', label: 'Movie', color: '#A855F7' },
              { id: 'night', icon: 'moon', label: 'Night', color: C.accent },
            ].map(scene => (
              <TouchableOpacity key={scene.id} testID={`scene-${scene.id}`} style={st.sceneBtn} onPress={() => activateScene(scene.id)}>
                <View style={[st.sceneIcon, { borderColor: scene.color + '40' }]}>
                  <Ionicons name={scene.icon as any} size={20} color={scene.color} />
                </View>
                <Text style={st.sceneLabel}>{scene.label}</Text>
              </TouchableOpacity>
            ))}
          </ScrollView>

          {/* Devices by Room */}
          {rooms.map(room => (
            <View key={room} style={st.roomSection}>
              <Text style={st.roomTitle}>{room.toUpperCase()}</Text>
              <View style={st.devicesGrid}>
                {devices.filter(d => d.room === room).map(dev => (
                  <TouchableOpacity key={dev.id} testID={`device-${dev.id}`}
                    style={[st.deviceCard, isOn(dev) && st.deviceOn]}
                    onPress={() => toggleDevice(dev.id)}>
                    {controlling === dev.id ? (
                      <ActivityIndicator size="small" color={DEVICE_COLORS[dev.type] || C.primary} />
                    ) : (
                      <Ionicons name={(DEVICE_ICONS[dev.type] || 'hardware-chip') as any} size={24}
                        color={isOn(dev) ? (DEVICE_COLORS[dev.type] || C.primary) : C.textTer} />
                    )}
                    <Text style={[st.devName, isOn(dev) && st.devNameOn]}>{dev.name}</Text>
                    <Text style={[st.devStatus, isOn(dev) && { color: C.ok }]}>
                      {dev.status.toUpperCase()}
                      {dev.temperature ? ` • ${dev.temperature}°C` : ''}
                      {dev.brightness ? ` • ${dev.brightness}%` : ''}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            </View>
          ))}

          {/* Info Banner */}
          <View style={st.infoBanner}>
            <Ionicons name="information-circle" size={18} color={C.info} />
            <Text style={st.infoText}>This is a simulation. Connect real devices (Philips Hue, Google Nest, etc.) via Settings when you have API keys.</Text>
          </View>
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
  simBadge: { marginLeft: 'auto', backgroundColor: 'rgba(59,130,246,0.15)', borderRadius: 4, paddingHorizontal: 8, paddingVertical: 3 },
  simText: { fontSize: 9, color: C.info, fontWeight: '700', letterSpacing: 1 },
  scrollContent: { padding: 16 },
  secTitle: { fontSize: 10, fontWeight: '700', color: C.textTer, letterSpacing: 2, marginBottom: 10 },
  scenesRow: { marginBottom: 20 },
  sceneBtn: { alignItems: 'center', marginRight: 16, gap: 6 },
  sceneIcon: { width: 52, height: 52, borderRadius: 14, borderWidth: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(255,255,255,0.02)' },
  sceneLabel: { fontSize: 10, color: C.textSec, fontWeight: '600' },
  roomSection: { marginBottom: 20 },
  roomTitle: { fontSize: 11, fontWeight: '700', color: C.primary, letterSpacing: 2, marginBottom: 10 },
  devicesGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  deviceCard: { width: '47%', backgroundColor: C.surfEl, borderWidth: 1, borderColor: C.border, borderRadius: 12, padding: 14, gap: 8, minWidth: 150 },
  deviceOn: { borderColor: C.hudBorder, backgroundColor: 'rgba(255,184,0,0.04)' },
  devName: { fontSize: 12, fontWeight: '600', color: C.textSec },
  devNameOn: { color: C.text },
  devStatus: { fontSize: 9, color: C.textTer, letterSpacing: 0.5 },
  infoBanner: { flexDirection: 'row', alignItems: 'flex-start', gap: 10, backgroundColor: 'rgba(59,130,246,0.06)', borderWidth: 1, borderColor: 'rgba(59,130,246,0.2)', borderRadius: 10, padding: 12, marginTop: 10 },
  infoText: { fontSize: 11, color: C.textSec, flex: 1, lineHeight: 16 },
});
