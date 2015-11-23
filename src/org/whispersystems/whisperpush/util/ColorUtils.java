package org.whispersystems.whisperpush.util;

import android.graphics.Color;

public class ColorUtils {

    private static final double NEAR_TO_SIMILAR_MAX_DIFF = 20.0;

    public static boolean isNearToSimilar(int color1, int color2) {
        return getColorsDiff(color1, color2) <= NEAR_TO_SIMILAR_MAX_DIFF;
    }

    public static double[] getLab(int color) {
        return xyzToLab(getXyz(color));
    }

    public static double[] getXyz(int color) {
        // Based on http://www.easyrgb.com/index.php?X=MATH&H=02
        double R = Color.red(color) / 255.0;
        double G = Color.green(color) / 255.0;
        double B = Color.blue(color) / 255.0;

        if ( R > 0.04045 ) {
            R = Math.pow(( ( R + 0.055 ) / 1.055 ),2.4);
        } else {
            R = R / 12.92;
        }

        if ( G > 0.04045 ) {
            G = Math.pow(( ( G + 0.055 ) / 1.055 ),2.4);
        } else {
            G = G / 12.92;
        }

        if ( B > 0.04045 ) {
            B = Math.pow(( ( B + 0.055 ) / 1.055 ), 2.4);
        } else {
            B = B / 12.92;
        }

        R *= 100;
        G *= 100;
        B *= 100;

        // Observer. = 2°, Illuminant = D65
        double X = R * 0.4124 + G * 0.3576 + B * 0.1805;
        double Y = R * 0.2126 + G * 0.7152 + B * 0.0722;
        double Z = R * 0.0193 + G * 0.1192 + B * 0.9505;
        return new double[]{X, Y, Z};
    }

    public static double[] xyzToLab(double[] xyz) {
        // Based on http://www.easyrgb.com/index.php?X=MATH&H=07
        double ref_Y = 100.000;
        double ref_Z = 108.883;
        double ref_X = 95.047; // Observer= 2°, Illuminant= D65
        double X = xyz[0] / ref_X;
        double Y = xyz[1] / ref_Y;
        double Z = xyz[2] / ref_Z;

        if ( X > 0.008856 ) {
            X = Math.pow(X, 1.0 / 3.0);
        } else {
            X = ( 7.787 * X ) + ( 16.0 / 116.0 );
        }

        if ( Y > 0.008856 ) {
            Y = Math.pow(Y, 1.0 / 3.0);
        } else {
            Y = ( 7.787 * Y ) + ( 16.0 / 116.0 );
        }

        if ( Z > 0.008856 ) {
            Z = Math.pow(Z, 1.0 / 3.0);
        } else {
            Z = ( 7.787 * Z ) + ( 16.0 / 116.0 );
        }

        double L = ( 116 * Y ) - 16;
        double a = 500 * ( X - Y );
        double b = 200 * ( Y - Z );
        return new double[]{L, a, b};
    }

    private static double degrees(double n) {
        return n * (180.0 / Math.PI);
    }

    private static double radians(double n) {
        return n * (Math.PI / 180.0);
    }

    private static double calculateHp(double x, double y) {
        if (x== 0 && y == 0) {
            return 0;
        } else {
            double hp = degrees(Math.atan2(x, y));
            if (hp >= 0) {
                return hp;
            } else {
                return hp + 360;
            }
        }
    }

    private static double calculateDHP(double C1, double C2, double h1p, double h2p) {
        if (C1*C2 == 0) {
            return 0;
        } else if(Math.abs(h2p - h1p) <= 180) {
            return h2p-h1p;
        } else if((h2p-h1p) > 180) {
            return (h2p-h1p)-360;
        } else if((h2p-h1p) < -180) {
            return (h2p-h1p)+360;
        } else {
            throw new IllegalArgumentException("Unexpected args");
        }
    }

    private static double calculateAHP(double C1, double C2, double h1p, double h2p) {
        if(C1*C2 == 0) {
            return h1p+h2p;
        } else if(Math.abs(h1p - h2p)<= 180) {
            return (h1p+h2p)/2.0;
        } else if((Math.abs(h1p - h2p) > 180) && ((h1p+h2p) < 360)) {
            return (h1p+h2p+360)/2.0;
        } else if((Math.abs(h1p - h2p) > 180) && ((h1p+h2p) >= 360)) {
            return (h1p+h2p-360)/2.0;
        } else {
            throw new IllegalArgumentException("Unexpected args");
        }
    }

    private static double calculateColorsDiffUsingCiede2000(double[] lab1, double[] lab2) {
        /**
         * Implemented as in "The CIEDE2000 Color-Difference Formula:
         * Implementation Notes, Supplementary Test Data, and Mathematical Observations"
         * by Gaurav Sharma, Wencheng Wu and Edul N. Dalal.
         */

        // Get L,a,b values for color 1
        double L1 = lab1[0];
        double a1 = lab1[1];
        double b1 = lab1[2];

        // Get L,a,b values for color 2
        double L2 = lab2[0];
        double a2 = lab2[1];
        double b2 = lab2[2];

        // Weight factors
        double kL = 1;
        double kC = 1;
        double kH = 1;

        /**
         * Step 1: Calculate C1p, C2p, h1p, h2p
         */
        double C1 = Math.sqrt(Math.pow(a1, 2) + Math.pow(b1, 2)); //(2)
        double C2 = Math.sqrt(Math.pow(a2, 2) + Math.pow(b2, 2)); //(2)

        double a_C1_C2 = (C1+C2)/2.0;             //(3)

        double G = 0.5 * (1 - Math.sqrt(Math.pow(a_C1_C2, 7.0) /
                (Math.pow(a_C1_C2, 7.0) + Math.pow(25.0, 7.0)))); //(4)

        double a1p = (1.0 + G) * a1; //(5)
        double a2p = (1.0 + G) * a2; //(5)

        double C1p = Math.sqrt(Math.pow(a1p, 2) + Math.pow(b1, 2)); //(6)
        double C2p = Math.sqrt(Math.pow(a2p, 2) + Math.pow(b2, 2)); //(6)

        double h1p = calculateHp(b1, a1p); //(7)
        double h2p = calculateHp(b2, a2p); //(7)

        /**
         * Step 2: Calculate dLp, dCp, dHp
         */
        double dLp = L2 - L1; //(8)
        double dCp = C2p - C1p; //(9)

        double dhp = calculateDHP(C1, C2, h1p, h2p); //(10)
        double dHp = 2*Math.sqrt(C1p * C2p)*Math.sin(radians(dhp) / 2.0); //(11)

        /**
         * Step 3: Calculate CIEDE2000 Color-Difference
         */
        double a_L = (L1 + L2) / 2.0; //(12)
        double a_Cp = (C1p + C2p) / 2.0; //(13)

        double a_hp = calculateAHP(C1, C2, h1p, h2p); //(14)
        double T = 1-0.17 *Math.cos(radians(a_hp - 30))+0.24*Math.cos(radians(2 * a_hp))+
                0.32*Math.cos(radians(3 * a_hp + 6))-0.20*Math.cos(radians(4 * a_hp - 63)); //(15)
        double d_ro = 30 * Math.exp(-(Math.pow((a_hp - 275) / 25.0, 2))); //(16)
        double RC = Math.sqrt((Math.pow(a_Cp, 7.0)) / (Math.pow(a_Cp, 7.0) + Math.pow(25.0, 7.0)));//(17)
        double SL = 1 + ((0.015 * Math.pow(a_L - 50, 2)) /
                Math.sqrt(20 + Math.pow(a_L - 50, 2.0)));//(18)
        double SC = 1 + 0.045 * a_Cp;//(19)
        double SH = 1 + 0.015 * a_Cp * T;//(20)
        double RT = -2 * RC * Math.sin(radians(2 * d_ro));//(21)
        double dE = Math.sqrt(Math.pow(dLp / (SL * kL), 2) + Math.pow(dCp / (SC * kC), 2) +
                Math.pow(dHp / (SH * kH), 2) + RT * (dCp / (SC * kC)) *
                (dHp / (SH * kH))); //(22)
        return dE;
    }

    public static double getColorsDiff(int color1, int color2) {
        return calculateColorsDiffUsingCiede2000(getLab(color1), getLab(color2));
    }

}
