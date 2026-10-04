import type { Options } from 'qr-code-styling';
import type { StyleConfig } from './types';

export function getFrameColor(style: StyleConfig): string {
  return style.frameColorMode === 'custom' ? style.frameColor : style.fgColor;
}

/** Construye las opciones de qr-code-styling para un tamaño dado. */
export function buildQrOptions(data: string, style: StyleConfig, size: number): Options {
  const k = size / 1024; // factor de escala para márgenes en px

  const rotation = (style.gradientRotation * Math.PI) / 180;

  const fgGradient =
    style.fgMode === 'gradient'
      ? {
          type: style.gradientType,
          rotation,
          colorStops: [
            { offset: 0, color: style.fgColor },
            { offset: 1, color: style.fgColor2 },
          ],
        }
      : undefined;

  const eyeColor = style.eyeColorMode === 'custom' ? style.eyeColor : style.fgColor;
  const eyeGradient = style.eyeColorMode === 'custom' ? undefined : fgGradient;

  let backgroundOptions: Options['backgroundOptions'] = { color: '#ffffff' };
  if (style.bgMode === 'transparent') backgroundOptions = { color: 'transparent' };
  else if (style.bgMode === 'solid') backgroundOptions = { color: style.bgColor };
  else
    backgroundOptions = {
      gradient: {
        type: style.gradientType,
        rotation,
        colorStops: [
          { offset: 0, color: style.bgColor },
          { offset: 1, color: style.bgColor2 },
        ],
      },
    };

  const options: Options = {
    width: size,
    height: size,
    type: 'canvas',
    data,
    margin: Math.round(style.margin * k),
    qrOptions: { errorCorrectionLevel: style.errorCorrection },
    imageOptions: {
      crossOrigin: 'anonymous',
      hideBackgroundDots: style.hideLogoBgDots,
      imageSize: style.logoSize,
      margin: Math.round(style.logoMargin * k),
    },
    dotsOptions: {
      type: style.dotType,
      color: style.fgColor,
      gradient: fgGradient,
    },
    cornersSquareOptions: {
      type: style.cornerSquareType,
      color: eyeColor,
      gradient: eyeGradient,
    },
    cornersDotOptions: {
      type: style.cornerDotType,
      color: eyeColor,
      gradient: eyeGradient,
    },
    backgroundOptions,
  };

  if (style.logo) options.image = style.logo;
  return options;
}
