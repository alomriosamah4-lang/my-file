import React from 'react';
import {View, Text, StyleSheet} from 'react-native';
import NativeSecurity from '../native/SecurityModule';
import {useNavigation} from '@react-navigation/native';
import {AppNavigationProp} from '../navigation/RootNavigator';
import {Colors} from '../styles/theme';
import PrimaryButton from '../components/PrimaryButton';

const SecurityError: React.FC = () => {
  const navigation = useNavigation<AppNavigationProp>();

  const retry = async () => {
    try {
      const ok = await NativeSecurity.nativeSelfTest();
      if (ok) {
        // go to onboarding if native checks pass
        navigation.navigate('Onboarding');
      }
    } catch (e) {
      // noop - remain on error screen
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>خطأ أمني</Text>
      <Text style={styles.msg}>فشل التحقق من مكونات التشفير الأصلية. يرجى إعادة المحاولة.</Text>
      <PrimaryButton title="إعادة المحاولة" onPress={retry} />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24, backgroundColor: Colors.dark.background},
  title: {fontSize: 22, fontWeight: '700', marginBottom: 12, color: '#fff'},
  msg: {fontSize: 14, color: 'rgba(255,255,255,0.7)', marginBottom: 20, textAlign: 'center'},
});

export default SecurityError;
