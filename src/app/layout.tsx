import type { Metadata, Viewport } from 'next';
import './globals.css';
import SwRegister from '@/components/SwRegister';

export const metadata: Metadata = {
  title: 'QR Studio — Generador de códigos QR con diseño',
  description:
    'Crea códigos QR personalizados con colores, degradados, formas, logos y marcos para tu negocio. Gratis, privado y 100% en tu dispositivo.',
  manifest: './manifest.webmanifest',
  appleWebApp: { capable: true, statusBarStyle: 'default', title: 'QR Studio' },
  icons: {
    icon: './icon.png',
    apple: './apple-icon.png',
  },
};

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  viewportFit: 'cover',
  themeColor: '#ffffff',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="es">
      <body>
        {children}
        <SwRegister />
      </body>
    </html>
  );
}
