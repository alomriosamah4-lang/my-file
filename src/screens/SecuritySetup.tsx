import React from 'react';
import { View, Text, StyleSheet, FlatList, TouchableOpacity, SafeAreaView } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { Colors } from '../styles/theme';
import { AppNavigationProp, RootStackParamList } from '../navigation/RootNavigator';

// As per USER_FLOW.md and UI_DESIGN.md
const SECURITY_OPTIONS = [
  { id: 'pin', title: 'رمز PIN', description: 'استخدم رمزًا من 4 أو 6 أرقام.' },
  { id: 'password', title: 'كلمة مرور', description: 'استخدم كلمة مرور قوية.' },
  { id: 'pattern', title: 'نمط', description: 'ارسم نمطًا لفتح القفل.' },
  { id: 'biometric', title: 'بصمة الإصبع / الوجه', description: 'استخدم المصادقة الحيوية لفتح سريع.' },
];

const SecuritySetup: React.FC = () => {
  const navigation = useNavigation<AppNavigationProp>();

  const handleSelectOption = (optionId: string) => {
    const screenName = `Setup${optionId.charAt(0).toUpperCase() + optionId.slice(1)}` as keyof RootStackParamList;
    navigation.navigate(screenName);
  };

  const renderItem = ({ item }: { item: typeof SECURITY_OPTIONS[0] }) => (
    <TouchableOpacity style={styles.card} onPress={() => handleSelectOption(item.id)}>
      {/* Placeholder for an Icon */}
      <View style={styles.iconPlaceholder} />
      <View style={styles.cardTextContainer}>
        <Text style={styles.cardTitle}>{item.title}</Text>
        <Text style={styles.cardDescription}>{item.description}</Text>
      </View>
    </TouchableOpacity>
  );

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>إعداد نظام الحماية</Text>
        <Text style={styles.subtitle}>اختر الطريقة التي تفضلها لحماية خزنتك.</Text>
      </View>
      <FlatList
        data={SECURITY_OPTIONS}
        renderItem={renderItem}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.listContent}
      />
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.dark.background,
  },
  header: {
    paddingHorizontal: 24,
    paddingTop: 24,
    paddingBottom: 16,
  },
  title: {
    fontSize: 28,
    fontWeight: '700',
    color: '#FFFFFF',
    textAlign: 'right',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    color: 'rgba(255, 255, 255, 0.8)',
    textAlign: 'right',
  },
  listContent: {
    paddingHorizontal: 24,
    paddingBottom: 24,
  },
  card: {
    backgroundColor: Colors.dark.surface,
    borderRadius: 16,
    padding: 20,
    flexDirection: 'row-reverse', // RTL layout
    alignItems: 'center',
    marginBottom: 16,
  },
  iconPlaceholder: {
    width: 48,
    height: 48,
    borderRadius: 24,
    backgroundColor: Colors.dark.accent[0] + '33', // Accent with opacity
    marginLeft: 16,
  },
  cardTextContainer: {
    flex: 1,
  },
  cardTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#FFFFFF',
    textAlign: 'right',
  },
  cardDescription: {
    fontSize: 14,
    color: 'rgba(255, 255, 255, 0.7)',
    textAlign: 'right',
    marginTop: 4,
  },
});

export default SecuritySetup;