<template>
  <svg
    class="home-hero-illu"
    viewBox="0 0 640 280"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
  >
    <defs>
      <linearGradient id="desk" x1="80" y1="40" x2="620" y2="270" gradientUnits="userSpaceOnUse">
        <stop stop-color="#EEF0FF"/>
        <stop offset="1" stop-color="#F8F3EE"/>
      </linearGradient>
      <linearGradient id="sheet" x1="210" y1="36" x2="430" y2="250" gradientUnits="userSpaceOnUse">
        <stop stop-color="#FFFFFF"/>
        <stop offset="1" stop-color="#F7F5FA"/>
      </linearGradient>
      <linearGradient id="scan" x1="360" y1="70" x2="520" y2="210" gradientUnits="userSpaceOnUse">
        <stop stop-color="#5B6CF7" stop-opacity="0.22"/>
        <stop offset="1" stop-color="#5B6CF7" stop-opacity="0"/>
      </linearGradient>
    </defs>

    <ellipse cx="430" cy="248" rx="168" ry="18" fill="#1C1A2E" opacity="0.06"/>

    <!-- desk blot -->
    <path d="M92 198c46-8 180-18 318 2 92 13 148 8 196-10v52H92v-44Z" fill="#E8E4F2" opacity="0.7"/>

    <!-- calendar / clip -->
    <rect x="78" y="54" width="88" height="98" rx="10" fill="#FFFDFB" stroke="#D8D3E6" stroke-width="1.4"/>
    <rect x="78" y="54" width="88" height="22" rx="10" fill="#5B6CF7"/>
    <rect x="78" y="66" width="88" height="10" fill="#5B6CF7"/>
    <circle cx="98" cy="64" r="3.2" fill="#FFFDFB"/>
    <circle cx="146" cy="64" r="3.2" fill="#FFFDFB"/>
    <rect x="92" y="88" width="16" height="12" rx="2" fill="#EDE9F8"/>
    <rect x="114" y="88" width="16" height="12" rx="2" fill="#5B6CF7" opacity="0.85"/>
    <rect x="136" y="88" width="16" height="12" rx="2" fill="#EDE9F8"/>
    <rect x="92" y="106" width="16" height="12" rx="2" fill="#EDE9F8"/>
    <rect x="114" y="106" width="16" height="12" rx="2" fill="#FF7E5D" opacity="0.85"/>
    <rect x="136" y="106" width="16" height="12" rx="2" fill="#EDE9F8"/>
    <rect x="92" y="124" width="16" height="12" rx="2" fill="#EDE9F8"/>
    <rect x="114" y="124" width="16" height="12" rx="2" fill="#EDE9F8"/>

    <!-- main timesheet -->
    <g transform="rotate(-6 320 150)">
      <rect x="196" y="42" width="248" height="176" rx="12" fill="url(#sheet)" stroke="#C9C3D8" stroke-width="1.4"/>
      <rect x="196" y="42" width="248" height="28" rx="12" fill="#F0F1FF"/>
      <rect x="196" y="58" width="248" height="12" fill="#F0F1FF"/>
      <rect x="214" y="52" width="86" height="8" rx="4" fill="#5B6CF7" opacity="0.75"/>
      <rect x="308" y="52" width="48" height="8" rx="4" fill="#5B6CF7" opacity="0.28"/>
      <path d="M214 86h196M214 108h168M214 130h184M214 152h152M214 174h176" stroke="#D8D3E6" stroke-width="4" stroke-linecap="round"/>
      <rect x="214" y="188" width="72" height="16" rx="8" fill="#34C77B" opacity="0.18"/>
      <path d="M222 196h20" stroke="#1A9E5C" stroke-width="2.2" stroke-linecap="round"/>
      <rect x="300" y="188" width="56" height="16" rx="8" fill="#5B6CF7" opacity="0.14"/>
    </g>

    <!-- clock -->
    <g transform="translate(118 168)">
      <circle cx="36" cy="36" r="30" fill="#FFFDFB" stroke="#1C1A2E" stroke-width="2"/>
      <circle cx="36" cy="36" r="24" fill="#F7F7FF"/>
      <path d="M36 18v18l11 7" stroke="#5B6CF7" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>
      <circle cx="36" cy="36" r="2.6" fill="#FF7E5D"/>
    </g>

    <!-- scan lens -->
    <g transform="translate(428 86)">
      <path d="M18 8h86v92H18z" fill="url(#scan)"/>
      <circle cx="58" cy="54" r="34" fill="#FFFDFB" stroke="#5B6CF7" stroke-width="3.2"/>
      <circle cx="58" cy="54" r="22" stroke="#5B6CF7" stroke-width="1.6" opacity="0.35"/>
      <path d="M84 80l22 22" stroke="#3E4CC4" stroke-width="6" stroke-linecap="round"/>
      <path d="M44 54h28" stroke="#FF7E5D" stroke-width="2.4" stroke-linecap="round"/>
    </g>

    <!-- stamp -->
    <g transform="translate(508 168)">
      <circle cx="40" cy="36" r="28" fill="#34C77B"/>
      <path d="M28 37.5 36.5 46 54 26" stroke="#fff" stroke-width="4.2" stroke-linecap="round" stroke-linejoin="round"/>
    </g>

    <!-- plant -->
    <g transform="translate(548 38)">
      <path d="M22 78c0-18 10-28 10-28s10 10 10 28" fill="#CDEDD8"/>
      <path d="M32 28c-14 8-16 24-8 34 10-8 16-20 8-34Z" fill="#34C77B"/>
      <path d="M32 30c12 10 14 24 6 34-8-10-16-20-6-34Z" fill="#1A9E5C"/>
      <rect x="24" y="76" width="16" height="18" rx="3" fill="#D8C7B4"/>
    </g>
  </svg>
</template>

<style scoped>
.home-hero-illu {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
