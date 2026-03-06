import React, {useRef, useState, useEffect, useCallback} from 'react';
import {View, Text, StyleSheet, FlatList, Dimensions, Animated, ViewToken} from 'react-native';
import PrimaryButton from '../components/PrimaryButton';
import {useNavigation} from '@react-navigation/native';
import {Colors} from '../styles/theme';
import { AppNavigationProp } from '../navigation/RootNavigator';

const {width} = Dimensions.get('window');

const PAGES = [
  {key: 'p1', title: 'حماية متقدمة', desc: 'احفظ ملفاتك بتشفير قوي'},
  {key: 'p2', title: 'إدارة الملفات', desc: 'إخفاء الصور والفيديوهات والملفات بسهولة'},
  {key: 'p3', title: 'الخصوصية', desc: 'لا يمكن لأي تطبيق الوصول لملفاتك'},
];

const Onboarding: React.FC = () => {
  const navigation = useNavigation<AppNavigationProp>();
  const flatListRef = useRef<FlatList<(typeof PAGES)[0]>>(null);
  const [index, setIndex] = useState(0);
  const titleOpacity = useRef(new Animated.Value(1)).current;

  useEffect(() => {
    // fade animation when index changes
    titleOpacity.setValue(0);
    Animated.timing(titleOpacity, {toValue: 1, duration: 360, useNativeDriver: true}).start();
  }, [index, titleOpacity]);

  const onViewableItemsChanged = useCallback(({viewableItems}: {viewableItems: Array<ViewToken>}) => {
    if (viewableItems.length > 0) {
      setIndex(viewableItems[0].index ?? 0);
    }
  }, []);

  const viewabilityConfig = useRef({
    itemVisiblePercentThreshold: 50,
  }).current;

  return (
    <View style={styles.container}>
      <FlatList
        ref={flatListRef}
        data={PAGES}
        keyExtractor={(i) => i.key}
        horizontal
        pagingEnabled
        showsHorizontalScrollIndicator={false}
        onViewableItemsChanged={onViewableItemsChanged}
        viewabilityConfig={viewabilityConfig}
        renderItem={({item}) => (
          <View style={[styles.page, {width}]}>
            <View style={styles.illustration} />
            <Animated.Text style={[styles.title, {opacity: titleOpacity}]}>{item.title}</Animated.Text>
            <Text style={styles.desc}>{item.desc}</Text>
          </View>
        )}
      />

      <View style={styles.footer}>
        <View style={styles.dots}>
          {PAGES.map((p, i) => (
            <View key={p.key} style={[styles.dot, index === i ? styles.dotActive : null]} />
          ))}
        </View>

        <PrimaryButton title={index === PAGES.length - 1 ? 'ابدأ الآن' : 'التالي'} onPress={() => {
          if (index === PAGES.length - 1) {
            navigation.navigate('SecuritySetup');
          } else {
            flatListRef.current?.scrollToIndex({index: index + 1});
          }
        }} />
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {flex: 1, backgroundColor: Colors.dark.background},
  page: {flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24},
  illustration: {width: 220, height: 160, backgroundColor: Colors.dark.surface, borderRadius: 12, marginBottom: 20},
  title: {fontSize: 22, fontWeight: '700', color: '#fff', marginBottom: 8},
  desc: {fontSize: 14, color: 'rgba(255,255,255,0.8)', textAlign: 'center'},
  footer: {padding: 20},
  dots: {flexDirection: 'row', justifyContent: 'center', marginBottom: 12},
  dot: {width: 8, height: 8, borderRadius: 4, backgroundColor: 'rgba(255,255,255,0.2)', marginHorizontal: 6},
  dotActive: {backgroundColor: Colors.dark.accent[0]},
});

export default Onboarding;
