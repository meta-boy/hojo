import { NativeModule, requireNativeModule } from 'expo';
import { EpaperConnectivityModuleEvents, NetworkInfo } from './EpaperConnectivity.types';

declare class EpaperConnectivityModule extends NativeModule<EpaperConnectivityModuleEvents> {
  connectToEpaperHotspot(): Promise<boolean>;
  bindToEpaperNetwork(): Promise<boolean>;
  unbindNetwork(): Promise<void>;
  testEpaperConnection(): Promise<boolean>;
  disconnectEpaperHotspot(): Promise<void>;
  isConnected(): Promise<boolean>;
  getNetworkInfo(): Promise<NetworkInfo | null>;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<EpaperConnectivityModule>('EpaperConnectivityModule');
