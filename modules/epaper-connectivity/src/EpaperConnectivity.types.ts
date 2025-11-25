export type NetworkInfo = {
  connected: boolean;
  ssid: string;
  targetIp: string;
  targetPort: number;
  hasWifi: boolean;
  hasInternet: boolean;
  interfaceName?: string;
};

export type EpaperConnectivityModuleEvents = {
  // Add events if any
};
