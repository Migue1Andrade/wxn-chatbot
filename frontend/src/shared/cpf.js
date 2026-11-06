export function cpfDigits(value) {
  return String(value || '').replace(/[^0-9]/g, '');
}
export function formatCpf(value) {
  return cpfDigits(value)
    .slice(0, 11)
    .replace(/^(\d{3})(\d)/, '$1.$2')
    .replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3')
    .replace(/\.(\d{3})(\d)/, '.$1-$2');
}
export function validCpf(value) {
  const digits = cpfDigits(value);
  if (digits.length !== 11 || /^(\d)\1{10}$/.test(digits)) {
    return false;
  }
  for (let size = 9; size <= 10; size++) {
    let sum = 0;
    for (let index = 0; index < size; index++) {
      sum += Number(digits[index]) * (size + 1 - index);
    }
    const remainder = sum % 11;
    if (Number(digits[size]) !== (remainder < 2 ? 0 : 11 - remainder)) {
      return false;
    }
  }
  return true;
}
