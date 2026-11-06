export function contactInitials(name) {
  const words = (name || '').trim().split(/\s+/).filter(Boolean);
  if (!words.length) {
    return 'CT';
  }
  if (/^[+\d ()-]+$/.test(name)) {
    return name.replace(/\D/g, '').slice(-2) || 'CT';
  }
  return (words[0][0] + (words.length > 1 ? words.at(-1)[0] : words[0][1] || '')).toUpperCase();
}
export function contactTone(name) {
  return ['violet', 'blue', 'amber', 'mint', 'rose', 'indigo'][
    Array.from(name || '').reduce((sum, character) => sum + character.charCodeAt(0), 0) % 6
  ];
}
