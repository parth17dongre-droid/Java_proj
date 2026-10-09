// At least 8 characters, one special character, one number
export function validatePassword(password) {
  if (password.length < 8) {
    return 'Password must be at least 8 characters long.';
  }
  if (!/[0-9]/.test(password)) {
    return 'Password must contain at least one number.';
  }
  if (!/[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?~`]/.test(password)) {
    return 'Password must contain at least one special character.';
  }
  return '';
}

// 3–20 characters, letters, numbers and underscores, must start with a letter
export function validateUsername(username) {
  if (!username) {
    return 'Username is required.';
  }
  if (!/^[A-Za-z][A-Za-z0-9_]{2,19}$/.test(username)) {
    return 'Username must be 3–20 characters, start with a letter, and contain only letters, numbers or underscores.';
  }
  return '';
}

export function validateEmail(email) {
  if (!email) return ''; // email is optional in your table
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return 'Please enter a valid email address.';
  }
  return '';
}

export function validateConfirmPassword(password, confirm) {
  if (password !== confirm) {
    return 'Passwords do not match.';
  }
  return '';
}