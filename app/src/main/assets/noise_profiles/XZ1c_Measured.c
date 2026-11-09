/* Sony Xperia XZ1 Compact (lilac), back camera, raw19 shim RAW.
 * Measured 2026-11-06: tripod ISO sweep, 20 Aperture RAW+JPEG shots, ISO 40 to 2884,
 * noise from Gr minus Gb in the centre 1024 px crop. Fit: variance = A*ISO*x + C*ISO^2 + D.
 * Same coefficients for all four planes (only green was measured). Floor D is constant:
 * the digital-gain divisor is set to the top ISO, 3200.
 */
#include <stdio.h>
double compute_noise_model_entry_S(int plane, int sens);
double compute_noise_model_entry_O(int plane, int sens);
int main(void) {
    for (int plane = 0; plane < 4; plane++) {
        for (int sens = 40; sens <= 3200; sens *= 2) {
            printf("%d,%d,%.6e,%.6e\n", plane, sens, compute_noise_model_entry_O(plane, sens), compute_noise_model_entry_S(plane, sens));
        }
    }
    return 0;
}

/* Planes are in R, Gr, Gb, B order. */
double compute_noise_model_entry_S(int plane, int sens) {
    static double noise_model_A[] = { 3.56e-06,3.56e-06,3.56e-06,3.56e-06 };
    static double noise_model_B[] = { 0.0,0.0,0.0,0.0 };
    double A = noise_model_A[plane];
    double B = noise_model_B[plane];
    double s = A * sens + B;
    return s < 0.0 ? 0.0 : s;
}

double compute_noise_model_entry_O(int plane, int sens) {
    static double noise_model_C[] = { 2.98e-11,2.98e-11,2.98e-11,2.98e-11 };
    static double noise_model_D[] = { 5.58e-05,5.58e-05,5.58e-05,5.58e-05 };
    double digital_gain = (sens / 3200.0) < 1.0 ? 1.0 : (sens / 3200.0);
    double C = noise_model_C[plane];
    double D = noise_model_D[plane];
    double o = C * sens * sens + D * digital_gain * digital_gain;
    return o < 0.0 ? 0.0 : o;
}
