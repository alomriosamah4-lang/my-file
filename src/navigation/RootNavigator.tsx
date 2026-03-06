import React from 'react';
import {createNativeStackNavigator, NativeStackNavigationProp} from '@react-navigation/native-stack';
import Splash from '../screens/Splash';
import Onboarding from '../screens/Onboarding';
import SecuritySetup from '../screens/SecuritySetup';
import VaultList from '../screens/VaultList';
import SetupPin from '../screens/SetupPin';
import SetupPassword from '../screens/SetupPassword';
import SetupPattern from '../screens/SetupPattern';
import SetupBiometric from '../screens/SetupBiometric';
import SecurityError from '../screens/SecurityError';

export type RootStackParamList = {
  Splash: undefined;
  Onboarding: undefined;
  SecuritySetup: undefined;
  VaultList: undefined;
  SetupPin: undefined;
  SetupPassword: undefined;
  SetupPattern: undefined;
  SetupBiometric: {type?: string; vaultId?: string} | undefined;
  SecurityError: undefined;
};

export type AppNavigationProp = NativeStackNavigationProp<RootStackParamList>;

const Stack = createNativeStackNavigator<RootStackParamList>();

const RootNavigator: React.FC = () => (
  <Stack.Navigator screenOptions={{headerShown: false}}>
    <Stack.Screen name="Splash" component={Splash} />
    <Stack.Screen name="Onboarding" component={Onboarding} />
    <Stack.Screen name="SecuritySetup" component={SecuritySetup} />
    <Stack.Screen name="VaultList" component={VaultList} />
    <Stack.Screen name="SetupPin" component={SetupPin} />
    <Stack.Screen name="SetupPassword" component={SetupPassword} />
    <Stack.Screen name="SetupPattern" component={SetupPattern} />
    <Stack.Screen name="SetupBiometric" component={SetupBiometric} />
    <Stack.Screen name="SecurityError" component={SecurityError} />
  </Stack.Navigator>
);

export default RootNavigator;
