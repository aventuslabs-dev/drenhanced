#version 150

// DR HUD overlay: two energy arcs around the crosshair, a mana bar under it,
// and an HP bar at the top of the screen. Distances are signed-distance fields.

in vec2 uv;

out vec4 fragColor;

uniform float energyPercent;
uniform float healthPercent;
uniform float aspectRatio;
uniform float manaPercent;

const float OPACITY = 0.75;
const vec4 COLOR_ENERGY_BASE  = vec4(0.8, 0.0, 0.2, OPACITY);
const vec4 COLOR_ENERGY_BASE2 = vec4(0.6, 0.0, 0.15, OPACITY);
const vec4 COLOR_ENERGY       = vec4(0.0, 0.8, 0.55, OPACITY);
const vec4 COLOR_ENERGY2      = vec4(0.0, 0.7, 0.7, OPACITY);

const vec4 COLOR_HP_BASE = vec4(0.8, 0.0, 0.2, 0.85);
const vec4 COLOR_HP      = vec4(0.0, 0.75, 0.6, 0.85);

const vec4 COLOR_MANA_BASE = vec4(0.8, 0.0, 0.2, OPACITY);
const vec4 COLOR_MANA      = vec4(0.37, 0.725, 1.0, OPACITY);

const float HEALTH_BAR_SIZE         = 0.4;
const float HEALTH_BAR_OUTLINE      = 0.0025;
const float HEALTH_BAR_THICKNESS    = 0.01;
const float HEALTH_BAR_Y            = 0.964;
const float BAR_MIX_THRESHOLD       = 0.0015;

const float ENERGY_ARC_DEGREES      = 22.0;
const float ENERGY_BAR_OUTLINE      = 0.002;
const float ENERGY_Y_OFFSET         = 0.125;
const float ENERGY_WIDTH            = 0.073;
const float ENERGY_RADIUS           = 0.16;
const float ENERGY_THICKNESS        = 0.005;

const float HEALTH_BAR_START = 0.5 - HEALTH_BAR_SIZE / 2.0;
const float ENERGY_START     = 0.5 - ENERGY_WIDTH / 2.0;

const float MANA_BAR_SIZE      = 0.073;
const float MANA_BAR_START     = 0.5 - MANA_BAR_SIZE / 2.0;
const float MANA_BAR_Y         = 0.42;
const float MANA_BAR_THICKNESS = 0.005;

float udSegment(vec2 a, vec2 b) {
    a.x *= aspectRatio;
    b.x *= aspectRatio;
    vec2 ba = b - a;
    vec2 pa = vec2(uv.x * aspectRatio, uv.y) - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - h * ba);
}

float sdArc(vec2 p, vec2 sca, vec2 scb, float ra, float rb) {
    p.x *= aspectRatio;
    p *= mat2(sca.x, sca.y, -sca.y, sca.x);
    p.x = abs(p.x);
    float k = (scb.y * p.x > scb.x * p.y) ? dot(p, scb) : length(p);
    return sqrt(dot(p, p) + ra * ra - 2.0 * ra * k) - rb;
}

float energyArc(bool flip) {
    float ta = flip ? radians(-90.0) : radians(90.0);
    float tb = radians(ENERGY_ARC_DEGREES);
    vec2 offset = vec2(0.0, ENERGY_Y_OFFSET);
    return sdArc(vec2(-0.5, -0.5) + uv + (flip ? -offset : offset),
                 vec2(sin(ta), cos(ta)), vec2(sin(tb), cos(tb)), ENERGY_RADIUS, ENERGY_THICKNESS);
}

float manaBar() {
    vec2 start = vec2(MANA_BAR_START + MANA_BAR_THICKNESS / 2.0, MANA_BAR_Y);
    return udSegment(start, start + vec2(MANA_BAR_SIZE - MANA_BAR_THICKNESS, 0.0)) - MANA_BAR_THICKNESS;
}

float healthBar() {
    vec2 start = vec2(HEALTH_BAR_START + HEALTH_BAR_THICKNESS / 2.0, HEALTH_BAR_Y);
    return udSegment(start, start + vec2(HEALTH_BAR_SIZE - HEALTH_BAR_THICKNESS, 0.0)) - HEALTH_BAR_THICKNESS;
}

vec4 shadeEnergy(float len, bool filled, bool green) {
    vec4 col = vec4(0.0);
    vec4 outline = vec4(vec3(0.0), COLOR_ENERGY_BASE.a);
    vec4 inner  = filled && green ? COLOR_ENERGY  : COLOR_ENERGY_BASE;
    vec4 inner2 = filled && green ? COLOR_ENERGY2 : COLOR_ENERGY_BASE2;

    vec4 fill = len > 0.1 ? inner : mix(inner2, inner, min(1.0, -len * 200.0));
    col = mix(col, fill, 1.0 - smoothstep(0.0, ENERGY_BAR_OUTLINE, len + ENERGY_BAR_OUTLINE / 3.0));
    if (!filled) {
        col = mix(col, outline, 1.0 - smoothstep(0.0, ENERGY_BAR_OUTLINE, abs(len)));
    }
    return col;
}

vec4 shadeLinear(float len, bool filled, float start, float size, float percent,
                 vec4 fillColor, vec4 baseColor, float outlineSize) {
    vec4 col = vec4(0.0);
    vec4 outline = vec4(vec3(0.0), baseColor.a);
    vec4 inner = baseColor;

    float filledEnd = start + size * percent;
    if (filled) {
        if (uv.x <= filledEnd) {
            inner = fillColor;
        } else {
            float d = uv.x - filledEnd;
            inner = d < BAR_MIX_THRESHOLD ? mix(fillColor, baseColor, d / BAR_MIX_THRESHOLD) : baseColor;
        }
    }

    col = mix(col, inner, 1.0 - smoothstep(0.0, outlineSize, len + outlineSize / 3.0));
    if (!filled) {
        col = mix(col, outline, 1.0 - smoothstep(0.0, outlineSize, abs(len)));
    }
    return col;
}

void main() {
    float topLen = energyArc(false);
    float btmLen = energyArc(true);
    float energyFill = (uv.x - ENERGY_START) / ENERGY_WIDTH;

    // Top arc shows 0-50% energy, bottom arc 50-100%.
    vec4 top = shadeEnergy(topLen, false, false);
    vec4 topFilled = shadeEnergy(topLen, true, energyFill < min(1.0, energyPercent * 2.0));
    vec4 colT = mix(top, topFilled, topFilled.a);

    vec4 btm = shadeEnergy(btmLen, false, false);
    vec4 btmFilled = shadeEnergy(btmLen, true, energyFill < min(1.0, max(0.0, (energyPercent - 0.5) * 2.0)));
    vec4 colB = mix(btm, btmFilled, btmFilled.a);

    float mLen = manaBar();
    float mPerc = clamp(manaPercent, 0.0, 1.0);
    vec4 mana = shadeLinear(mLen, false, MANA_BAR_START, MANA_BAR_SIZE, mPerc, COLOR_MANA, COLOR_MANA_BASE, ENERGY_BAR_OUTLINE);
    vec4 manaFilled = shadeLinear(mLen, true, MANA_BAR_START, MANA_BAR_SIZE, mPerc, COLOR_MANA, COLOR_MANA_BASE, ENERGY_BAR_OUTLINE);
    vec4 colM = mix(mana, manaFilled, mPerc > 0.0 ? manaFilled.a : 0.0);

    float hLen = healthBar();
    float hPerc = clamp(healthPercent, 0.0, 1.0);
    vec4 health = shadeLinear(hLen, false, HEALTH_BAR_START, HEALTH_BAR_SIZE, hPerc, COLOR_HP, COLOR_HP_BASE, HEALTH_BAR_OUTLINE);
    vec4 healthFilled = shadeLinear(hLen, true, HEALTH_BAR_START, HEALTH_BAR_SIZE, hPerc, COLOR_HP, COLOR_HP_BASE, HEALTH_BAR_OUTLINE);
    vec4 colH = mix(health, healthFilled, healthFilled.a);

    fragColor = vec4(0.0);
    if (colT.a > 0.0) {
        fragColor = vec4(colT.rgb, colT.a * 0.85);
    } else if (colB.a > 0.0) {
        fragColor = vec4(colB.rgb, colB.a * 0.85);
    } else if (colM.a > 0.0) {
        fragColor = vec4(colM.rgb, colM.a * 0.85);
    } else if (colH.a > 0.0) {
        fragColor = vec4(colH.rgb, colH.a * 0.95);
    }

    // The original left fragColor unset here (undefined output on strict drivers).
    if (fragColor.a <= 0.0) {
        discard;
    }
}
