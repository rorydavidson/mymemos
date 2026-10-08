import type { JSX } from 'preact'

// A small stroke icon set drawn for this app: 24px grid, 1.8px round strokes, currentColor.
const paths: Record<string, JSX.Element> = {
  notes: <><rect x="4" y="3.5" width="16" height="17" rx="3" /><path d="M8 8.5h8M8 12h8M8 15.5h5" /></>,
  tasks: <><rect x="3.5" y="4" width="6" height="6" rx="1.5" /><path d="m5 7 1.2 1.2L8.4 6M13 7h7.5M3.5 14h6v6h-6zM13 17h7.5" /></>,
  review: <><path d="M12 3.5v3M12 17.5v3M3.5 12h3M17.5 12h3" /><circle cx="12" cy="12" r="5.5" /><path d="M12 9.5V12l1.6 1.6" /></>,
  tag: <><path d="M3.5 12.2V4.5a1 1 0 0 1 1-1h7.7a1 1 0 0 1 .7.3l7.6 7.6a1 1 0 0 1 0 1.4l-7.7 7.7a1 1 0 0 1-1.4 0l-7.6-7.6a1 1 0 0 1-.3-.7Z" /><circle cx="8" cy="8" r="1.4" /></>,
  archive: <><rect x="3" y="4" width="18" height="4.5" rx="1.2" /><path d="M5 8.5V19a1.5 1.5 0 0 0 1.5 1.5h11A1.5 1.5 0 0 0 19 19V8.5M10 12.5h4" /></>,
  bolt: <path d="M13 2.5 5 13.5h6l-1 8 8-11h-6l1-8Z" />,
  place: <><path d="M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 0 1 13 0c0 5.4-6.5 11-6.5 11Z" /><circle cx="12" cy="10" r="2.4" /></>,
  graph: <><circle cx="6" cy="6" r="2.5" /><circle cx="18" cy="8" r="2.5" /><circle cx="9" cy="18" r="2.5" /><path d="M8.3 7 15.7 7.6M7 8.3l1.4 7.3M16.6 10.1 10.8 16.3" /></>,
  bell: <><path d="M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 2h-15l1.5-2Z" /><path d="M10 20.5a2 2 0 0 0 4 0" /></>,
  settings: <><circle cx="12" cy="12" r="3" /><path d="M12 2.8v2.4M12 18.8v2.4M2.8 12h2.4M18.8 12h2.4M5.5 5.5l1.7 1.7M16.8 16.8l1.7 1.7M5.5 18.5l1.7-1.7M16.8 7.2l1.7-1.7" /></>,
  person: <><circle cx="12" cy="8" r="4" /><path d="M4 20.5c1.2-4 4.3-6 8-6s6.8 2 8 6" /></>,
  shield: <path d="M12 3 4.5 6v5.5c0 4.6 3.2 8.2 7.5 9.5 4.3-1.3 7.5-4.9 7.5-9.5V6L12 3Z" />,
  plus: <path d="M12 5v14M5 12h14" />,
  search: <><circle cx="11" cy="11" r="6.5" /><path d="m16 16 4.5 4.5" /></>,
  close: <path d="M6 6l12 12M18 6 6 18" />,
  back: <path d="M15 5 8 12l7 7" />,
  chevron: <path d="m6 9 6 6 6-6" />,
  more: <><circle cx="12" cy="5.5" r="1.3" /><circle cx="12" cy="12" r="1.3" /><circle cx="12" cy="18.5" r="1.3" /></>,
  pin: <><path d="M9 3.5h6l-1 6 3.5 3.5v1.5h-11V13L10 9.5l-1-6Z" /><path d="M12 14.5V21" /></>,
  lock: <><rect x="5" y="10.5" width="14" height="10" rx="2.5" /><path d="M8 10.5V8a4 4 0 0 1 8 0v2.5" /></>,
  unlock: <><rect x="5" y="10.5" width="14" height="10" rx="2.5" /><path d="M8 10.5V8a4 4 0 0 1 7.7-1.5" /></>,
  edit: <><path d="M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16v4Z" /><path d="m13.5 6.5 4 4" /></>,
  trash: <><path d="M4.5 7h15M9.5 7V4.5h5V7M6.5 7l1 13h9l1-13" /></>,
  sync: <><path d="M19.5 10A7.5 7.5 0 0 0 6 6.6L4.5 8.5M4.5 14A7.5 7.5 0 0 0 18 17.4l1.5-1.9" /><path d="M4.5 4v4.5H9M19.5 20v-4.5H15" /></>,
  cloudOff: <><path d="M7 18.5h10.5a4 4 0 0 0 1-7.9A6 6 0 0 0 8 8.2M5.6 10.4A4.3 4.3 0 0 0 7 18.5" /><path d="m3.5 3.5 17 17" /></>,
  attach: <path d="m20 11.5-8 8a5 5 0 0 1-7-7l8.5-8.5a3.3 3.3 0 0 1 4.7 4.7l-8.5 8.5a1.7 1.7 0 0 1-2.4-2.4l7.8-7.8" />,
  image: <><rect x="3.5" y="4.5" width="17" height="15" rx="2.5" /><circle cx="9" cy="10" r="1.8" /><path d="m4 17.5 5-4.5 4 3.5 2.5-2 4.5 3.5" /></>,
  bold: <path d="M7 4.5h6a3.75 3.75 0 0 1 0 7.5H7zM7 12h7a4 4 0 0 1 0 8H7z" />,
  italic: <path d="M10 4.5h8M6 19.5h8M14.5 4.5l-5 15" />,
  code: <path d="m8.5 7-5 5 5 5M15.5 7l5 5-5 5" />,
  list: <><path d="M9 6.5h11M9 12h11M9 17.5h11" /><circle cx="4.5" cy="6.5" r="1" /><circle cx="4.5" cy="12" r="1" /><circle cx="4.5" cy="17.5" r="1" /></>,
  checkbox: <><rect x="4" y="4" width="16" height="16" rx="3" /><path d="m8 12 3 3 5-6" /></>,
  heading: <path d="M6 4.5v15M18 4.5v15M6 12h12" />,
  quote: <path d="M5 11h4v6H5zm0 0c0-3 1-5 4-6M14 11h4v6h-4zm0 0c0-3 1-5 4-6" />,
  link: <><path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1" /><path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1" /></>,
  template: <><rect x="4" y="3.5" width="16" height="17" rx="2.5" /><path d="M4 9h16M10 9v11.5" /></>,
  palette: <><path d="M12 3.5a8.5 8.5 0 1 0 0 17c1.4 0 2-1 1.5-2.2-.6-1.2.1-2.3 1.4-2.3h2.3a3.3 3.3 0 0 0 3.3-3.3c0-5-3.9-9.2-8.5-9.2Z" /><circle cx="7.5" cy="11.5" r="1.1" /><circle cx="10" cy="7.5" r="1.1" /><circle cx="15" cy="7.8" r="1.1" /></>,
  eye: <><path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z" /><circle cx="12" cy="12" r="3" /></>,
  share: <><circle cx="18" cy="5.5" r="2.5" /><circle cx="6" cy="12" r="2.5" /><circle cx="18" cy="18.5" r="2.5" /><path d="m8.2 10.8 7.6-4.1M8.2 13.2l7.6 4.1" /></>,
  alarm: <><circle cx="12" cy="13" r="7" /><path d="M12 9.5V13l2.5 2M4 5.5 6.5 3M20 5.5 17.5 3" /></>,
  comment: <path d="M4 5.5A2 2 0 0 1 6 3.5h12a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H10l-4.5 4v-4H6a2 2 0 0 1-2-2v-9Z" />,
  smile: <><circle cx="12" cy="12" r="8.5" /><path d="M8.5 14.5a4.5 4.5 0 0 0 7 0" /><circle cx="9" cy="10" r=".8" /><circle cx="15" cy="10" r=".8" /></>,
  calendar: <><rect x="3.5" y="5" width="17" height="15.5" rx="2.5" /><path d="M3.5 10h17M8 3v4M16 3v4" /></>,
  check: <path d="m5 12.5 4.5 4.5L19 7.5" />,
  undo: <path d="M9 14 4 9l5-5M4 9h10a6 6 0 0 1 0 12h-3" />,
  download: <path d="M12 3.5v12m0 0-4.5-4.5M12 15.5l4.5-4.5M4.5 20.5h15" />,
  upload: <path d="M12 15.5v-12m0 0L7.5 8M12 3.5 16.5 8M4.5 20.5h15" />,
  compact: <path d="M4 6h16M4 10h16M4 14h16M4 18h16" />,
  cards: <><rect x="4" y="3.5" width="16" height="7" rx="2" /><rect x="4" y="13.5" width="16" height="7" rx="2" /></>,
  sort: <path d="M7 4v16m0 0-3-3m3 3 3-3M17 20V4m0 0-3 3m3-3 3 3" />,
  sun: <><circle cx="12" cy="12" r="4" /><path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4" /></>,
  moon: <path d="M19.5 14.5A8 8 0 0 1 9.5 4.5a8 8 0 1 0 10 10Z" />,
  logout: <><path d="M14 4.5h4a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2h-4" /><path d="M10 16.5 5.5 12 10 7.5M5.5 12H15" /></>,
  warning: <><path d="M12 3.5 2.5 20h19L12 3.5Z" /><path d="M12 10v4.5M12 17.2v.1" /></>,
  swipe: <path d="M4 12h16m0 0-4-4m4 4-4 4" />,
  visibility: <><circle cx="12" cy="12" r="8.5" /><path d="M3.5 12h17M12 3.5c2.5 2.5 3.5 5.5 3.5 8.5s-1 6-3.5 8.5c-2.5-2.5-3.5-5.5-3.5-8.5s1-6 3.5-8.5Z" /></>,
}

export function Icon({ name, size = 22, class: cls }: { name: keyof typeof paths | string; size?: number; class?: string }) {
  return (
    <svg
      class={cls}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      {paths[name] ?? paths.notes}
    </svg>
  )
}
