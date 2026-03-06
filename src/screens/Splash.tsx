import React, {useEffect} from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import PrimaryButton from '../components/PrimaryButton';
import { Colors } from '../styles/theme';
import { AppNavigationProp } from '../navigation/RootNavigator';
import NativeSecurity from '../native/SecurityModule';

const Splash: React.FC = () => {
  const navigation = useNavigation<AppNavigationProp>();

  useEffect(() => {
    const checkSecurity = async () => {
      const secure = await NativeSecurity.nativeSelfTest();
      if (!secure) {
        navigation.replace('SecurityError');
      }
    };
    checkSecurity();
  }, [navigation]);

  const handleStart = () => {
    // As per USER_FLOW, Splash -> Onboarding
    navigation.navigate('Onboarding');
  };

  return (
    <View style={styles.container}>
      <View style={styles.content}>
        {/* Placeholder for Logo */}
        <View style={styles.logoPlaceholder} />
        <Text style={styles.appName}>خزنتي</Text>
        <Text style={styles.tagline}>أمان ملفاتك هو أولويتنا</Text>
      </View>
      <View style={styles.footer}>
        <PrimaryButton title="بدء الاستخدام" onPress={handleStart} />
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.dark.background,
    justifyContent: 'space-between',
    padding: 24,
  },
  content: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  logoPlaceholder: {
    width: 120,
    height: 120,
    borderRadius: 24,
    backgroundColor: Colors.dark.surface,
    marginBottom: 24,
  },
  appName: {
    fontSize: 32,
    fontWeight: 'bold',
    color: '#FFFFFF',
    marginBottom: 8,
  },
  tagline: {
    fontSize: 16,
    color: 'rgba(255, 255, 255, 0.7)',
  },
  footer: {
    paddingBottom: 20, // For safe area
  },
});

export default Splash;