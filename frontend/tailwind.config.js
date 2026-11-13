/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      fontFamily: {
        sans: ['"IBM Plex Sans"', "Inter", "system-ui", "sans-serif"],
        mono: ['"IBM Plex Mono"', "ui-monospace", "monospace"],
      },
      colors: {
        frame: "#f2f6f7",
        sheet: "#ffffff",
        ink: "#131416",
        muted: "#5d5f60",
        line: "#e8eaec",
        accent: "#1b6c80",
      },
    },
  },
  plugins: [],
};
