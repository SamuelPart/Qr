import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'pe.qrstudio.app',
  appName: 'QR Studio',
  webDir: 'out',
  server: {
    // sirve la app local por HTTPS: necesario para cámara (getUserMedia) y service worker
    androidScheme: 'https',
  },
  android: {
    allowMixedContent: false,
  },
};

export default config;
