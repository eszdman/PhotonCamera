float sigmoid(float val, float transfer) {
    if (val > transfer) {
        // This variable maps the cut off point in the linear curve to the sigmoid
        float a = log((1.f + transfer) / (1.f - transfer)) / transfer;

        // Transform val using the sigmoid curve
        val = 2.f / (1.f + exp(-a * val)) - 1.f;
    }
    return val;
}
