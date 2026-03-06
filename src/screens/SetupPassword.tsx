import React, {useState, useRef, useEffect} from 'react';
import {View, Text, StyleSheet, Alert, Switch, SafeAreaView} from 'react-native';
import TextField from '../components/TextField';
import PrimaryButton from '../components/PrimaryButton';
import {useNavigation} from '@react-navigation/native';
import {Colors} from '../styles/theme';
import NativeSecurity from '../native/SecurityModule';
import {AppNavigationProp} from '../navigation/RootNavigator';

const SetupPassword: React.FC = () => {
  const navigation = useNavigation<AppNavigationProp>();
  const [pwd, setPwd] = useState('');
  const [confirm, setConfirm] = useState('');
  const [loading, setLoading] = useState(false);
  const [useBiometric, setUseBiometric] = useState(false);
  const isMounted = useRef(true);

  useEffect(() => {
    return () => { isMounted.current = false; };
  }, []);

  const handleSave = async () => {
    if (pwd.length < 8) return Alert.alert('خطأ', 'كلمة المرور قصيرة جدًا. يجب أن تكون 8 أحرف على الأقل.');
    if (pwd !== confirm) return Alert.alert('خطأ', 'كلمتا المرور غير متطابقتين.');
    setLoading(true);

    try {
      const res = await NativeSecurity.createVault('default', pwd, {useBiometric: useBiometric});
      const vaultId = res?.vaultId;
      if (useBiometric && vaultId) {
        try {
          await NativeSecurity.enableBiometric(vaultId);
        } catch (e: any) {
          if (isMounted.current) {
            Alert.alert('تنبيه', e?.message || 'تعذّر تفعيل المصادقة البيومترية');
          }
        }
      }
      if (isMounted.current) {
        navigation.navigate('VaultList');
      }
    } catch (err: any) {
      if (isMounted.current) {
        Alert.alert('خطأ', err?.message || 'فشل إنشاء الخزنة');
      }
    } finally {
      if (isMounted.current) setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>إنشاء كلمة مرور</Text>
      <TextField placeholder="أدخل كلمة المرور" value={pwd} onChangeText={setPwd} secureTextEntry />
      <TextField placeholder="تأكيد كلمة المرور" value={confirm} onChangeText={setConfirm} secureTextEntry />
      <View style={styles.switchRow}>
        <Text style={styles.switchLabel}>تفعيل المصادقة البيومترية بعد الإنشاء</Text>
        <Switch value={useBiometric} onValueChange={setUseBiometric} trackColor={{false: Colors.dark.surface, true: Colors.dark.accent[0]}} />
      </View>
      <PrimaryButton title="حفظ" onPress={handleSave} style={{marginTop: 24}} loading={loading} disabled={loading} />
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {flex: 1, padding: 24, backgroundColor: Colors.dark.background},
  title: {fontSize: 28, fontWeight: '700', color: '#fff', marginBottom: 20, textAlign: 'right'},
  switchRow: {flexDirection: 'row-reverse', alignItems: 'center', justifyContent: 'space-between', marginTop: 16, paddingHorizontal: 8},
  switchLabel: {color: 'rgba(255,255,255,0.9)', fontSize: 16, marginLeft: 12},
});

export default SetupPassword;
