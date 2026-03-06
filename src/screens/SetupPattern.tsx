import React, {useState} from 'react';
import {View, Text, StyleSheet, Alert} from 'react-native';
import PrimaryButton from '../components/PrimaryButton';
import {useNavigation} from '@react-navigation/native';
import {Colors} from '../styles/theme';
import {AppNavigationProp} from '../navigation/RootNavigator';

const SetupPattern: React.FC = () => {
  const navigation = useNavigation<AppNavigationProp>();
  const [pattern, setPattern] = useState('');

  const handleSave = () => {
    if (pattern.length < 4) return Alert.alert('خطأ', 'الرجاء رسم نمط أطول.');
    // placeholder: pattern capture and confirm
    navigation.navigate('VaultList');
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>إنشاء نمط قفل</Text>
      <View style={styles.placeholder} />
      <PrimaryButton title="حفظ" onPress={handleSave} style={{marginTop: 12}} />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {flex: 1, padding: 20, backgroundColor: Colors.dark.background},
  title: {fontSize: 20, fontWeight: '700', color: '#fff', marginBottom: 12},
  placeholder: {height: 240, backgroundColor: Colors.dark.surface, borderRadius: 12},
});

export default SetupPattern;
