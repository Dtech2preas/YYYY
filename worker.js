function getCorsHeaders(request) {
  const origin = request.headers.get("Origin") || "";

  // Check if it's one of the allowed domains
  if (
    origin.endsWith(".dtech-services.co.za") ||
    origin.endsWith(".preasx24.co.za") ||
    origin === "https://student.dtech-services.co.za"
  ) {
    return {
      'Access-Control-Allow-Origin': origin,
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Admin-Secret',
    };
  }

  // Default fallback for other requests (or you can restrict it further if needed)
  return {
    'Access-Control-Allow-Origin': 'https://student.dtech-services.co.za',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Admin-Secret',
  };
}

export default {
  async fetch(request, env, ctx) {
    if (request.method === 'OPTIONS') {
      return new Response(null, { headers: getCorsHeaders(request) });
    }

    const url = new URL(request.url);
    const path = url.pathname;

    try {
      if (path === '/register' && request.method === 'POST') {
        return await handleRegister(request, env);
      } else if (path === '/login' && request.method === 'POST') {
        return await handleLogin(request, env);
      } else if (path === '/sync-points' && request.method === 'POST') {
        return await handleSyncPoints(request, env);
      } else if (path === '/user' && request.method === 'GET') {
        return await handleGetUser(request, env);
      } else if (path === '/withdraw' && request.method === 'POST') {
        return await handleWithdraw(request, env);
      } else if (path === '/withdrawals' && request.method === 'GET') {
        return await handleGetUserWithdrawals(request, env);
      } else if (path === '/admin/users' && request.method === 'GET') {
        return await handleAdminUsers(request, env);
      } else if (path === '/admin/action' && request.method === 'POST') {
        return await handleAdminUserAction(request, env);
      } else if (path === '/admin/withdrawals' && request.method === 'GET') {
        return await handleAdminWithdrawals(request, env);
      } else if (path === '/admin/withdrawal-action' && request.method === 'POST') {
        return await handleAdminWithdrawalAction(request, env);
      } else {
        return new Response('Not Found', { status: 404, headers: getCorsHeaders(request) });
      }
    } catch (err) {
      return new Response(JSON.stringify({ error: err.message }), { status: 500, headers: getCorsHeaders(request) });
    }
  },
};

// --- Helpers ---

async function hashPassword(password) {
  const msgBuffer = new TextEncoder().encode(password);
  const hashBuffer = await crypto.subtle.digest('SHA-256', msgBuffer);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}

// --- Handlers ---

async function handleRegister(request, env) {
  const { username, password, email, whatsapp } = await request.json();

  if (!username || !password) {
    return new Response(JSON.stringify({ error: 'Missing username or password' }), { status: 400, headers: getCorsHeaders(request) });
  }

  const existingUser = await env.USERS.get(username);
  if (existingUser) {
    return new Response(JSON.stringify({ error: 'Username already taken' }), { status: 409, headers: getCorsHeaders(request) });
  }

  const passwordHash = await hashPassword(password);

  const userData = {
    username: username,
    passwordHash: passwordHash,
    points: 0,
    status: 'active',
    is_banned: false,
    created_at: new Date().toISOString(),
    email: email || '',
    whatsapp: whatsapp || ''
  };

  await env.USERS.put(username, JSON.stringify(userData));

  // Add to user list
  let userList = [];
  try {
      const raw = await env.USERS.get('SYSTEM:USER_LIST');
      if (raw) userList = JSON.parse(raw);
  } catch(e) {}
  if(!userList.includes(username)) {
      userList.push(username);
      await env.USERS.put('SYSTEM:USER_LIST', JSON.stringify(userList));
  }

  return new Response(JSON.stringify({ message: 'User registered successfully' }), { status: 201, headers: getCorsHeaders(request) });
}

async function handleLogin(request, env) {
  const { username, password } = await request.json();
  if (!username || !password) return new Response(JSON.stringify({ error: 'Missing credentials' }), { status: 400, headers: getCorsHeaders(request) });

  const userJson = await env.USERS.get(username);
  if (!userJson) return new Response(JSON.stringify({ error: 'User not found' }), { status: 404, headers: getCorsHeaders(request) });

  const user = JSON.parse(userJson);
  const inputHash = await hashPassword(password);

  if (user.passwordHash !== inputHash) {
      if (user.password && user.password === password) {
          user.passwordHash = await hashPassword(password);
          delete user.password;
      } else {
          return new Response(JSON.stringify({ error: 'Invalid credentials' }), { status: 401, headers: getCorsHeaders(request) });
      }
  }

  if (user.is_banned) {
      return new Response(JSON.stringify({ error: 'Your account is banned' }), { status: 403, headers: getCorsHeaders(request) });
  }

  const token = crypto.randomUUID();
  user.token = token;
  if(user.points === undefined) user.points = 0;
  await env.USERS.put(username, JSON.stringify(user));

  return new Response(JSON.stringify({
      message: 'Login successful',
      points: user.points,
      token: token,
      username: username
  }), { status: 200, headers: getCorsHeaders(request) });
}

async function handleGetUser(request, env) {
  const url = new URL(request.url);
  const username = url.searchParams.get('username');
  if (!username) return new Response(JSON.stringify({ error: 'Missing username' }), { status: 400, headers: getCorsHeaders(request) });

  const userJson = await env.USERS.get(username);
  if (!userJson) return new Response(JSON.stringify({ error: 'User not found' }), { status: 404, headers: getCorsHeaders(request) });

  const user = JSON.parse(userJson);

  if (user.is_banned) {
      return new Response(JSON.stringify({ error: 'Your account is banned' }), { status: 403, headers: getCorsHeaders(request) });
  }

  if(user.points === undefined) user.points = 0;

  return new Response(JSON.stringify({
      username: username,
      points: user.points
  }), { status: 200, headers: getCorsHeaders(request) });
}

async function handleSyncPoints(request, env) {
  // Check App Secret User-Agent
  const userAgent = request.headers.get('User-Agent') || '';
  if (!userAgent.includes('DTechApp-Secret-9f8d7b6a')) {
      return new Response(JSON.stringify({ error: 'Unauthorized request origin' }), { status: 403, headers: getCorsHeaders(request) });
  }

  const { username, token, points } = await request.json();

  if (!username) return new Response(JSON.stringify({ error: 'Missing username' }), { status: 400, headers: getCorsHeaders(request) });
  if (!points || isNaN(points) || points <= 0) return new Response(JSON.stringify({ error: 'Invalid points' }), { status: 400, headers: getCorsHeaders(request) });

  const userJson = await env.USERS.get(username);
  if (!userJson) return new Response(JSON.stringify({ error: 'User not found' }), { status: 404, headers: getCorsHeaders(request) });

  const user = JSON.parse(userJson);

  if (user.is_banned) return new Response(JSON.stringify({ error: 'Account banned' }), { status: 403, headers: getCorsHeaders(request) });
  if (user.token !== token) return new Response(JSON.stringify({ error: 'Invalid token' }), { status: 403, headers: getCorsHeaders(request) });

  if(user.points === undefined) user.points = 0;
  user.points += parseInt(points);

  await env.USERS.put(username, JSON.stringify(user));

  return new Response(JSON.stringify({
      message: 'Points synced successfully',
      points: user.points,
      added: points
  }), { status: 200, headers: getCorsHeaders(request) });
}

async function handleWithdraw(request, env) {
  const data = await request.json();
  const { username, token, points, amount, method, details } = data;

  if (!username || !token || !points || isNaN(points) || !amount || isNaN(amount) || !method || !details) {
      return new Response(JSON.stringify({ error: `Missing required fields` }), { status: 400, headers: getCorsHeaders(request) });
  }

  const userJson = await env.USERS.get(username);
  if (!userJson) return new Response(JSON.stringify({ error: 'User not found' }), { status: 404, headers: getCorsHeaders(request) });

  const user = JSON.parse(userJson);

  if (user.is_banned) {
      return new Response(JSON.stringify({ error: 'Account banned' }), { status: 403, headers: getCorsHeaders(request) });
  }
  if (user.token !== token) {
      return new Response(JSON.stringify({ error: 'Invalid token' }), { status: 403, headers: getCorsHeaders(request) });
  }

  if (user.points < points) {
      return new Response(JSON.stringify({ error: 'Insufficient points' }), { status: 400, headers: getCorsHeaders(request) });
  }

  // Deduct points
  user.points -= points;

  // Record Withdrawal Request
  const withdrawal = {
      id: crypto.randomUUID(),
      username: username,
      points: points,
      amount: amount,
      method: method,
      details: details, // Saving simplified details payload straight from frontend
      status: 'pending',
      timestamp: Date.now()
  };

  // Save updated user
  await env.USERS.put(username, JSON.stringify(user));

  // Save to Global Withdrawals List
  let withdrawals = [];
  try {
      const raw = await env.USERS.get('SYSTEM:WITHDRAWALS');
      if (raw) withdrawals = JSON.parse(raw);
  } catch(e) {}

  withdrawals.push(withdrawal);
  await env.USERS.put('SYSTEM:WITHDRAWALS', JSON.stringify(withdrawals));

  return new Response(JSON.stringify({
      message: 'Withdrawal requested successfully',
      points: user.points
  }), { status: 200, headers: getCorsHeaders(request) });
}

async function handleGetUserWithdrawals(request, env) {
    const url = new URL(request.url);
    const username = url.searchParams.get('username');
    if (!username) return new Response(JSON.stringify({ error: 'Missing username' }), { status: 400, headers: getCorsHeaders(request) });

    let withdrawals = [];
    try {
        const raw = await env.USERS.get('SYSTEM:WITHDRAWALS');
        if (raw) withdrawals = JSON.parse(raw);
    } catch(e) {}

    const userWithdrawals = withdrawals.filter(w => w.username === username);

    return new Response(JSON.stringify(userWithdrawals), { status: 200, headers: getCorsHeaders(request) });
}

// --- ADMIN HANDLERS ---

async function handleAdminUsers(request, env) {
    const secret = request.headers.get('X-Admin-Secret');
    if (secret !== env.ADMIN_SECRET) return new Response(JSON.stringify({ error: 'Unauthorized' }), { status: 403, headers: getCorsHeaders(request) });
    let userList = [];
    try {
        const raw = await env.USERS.get('SYSTEM:USER_LIST');
        if (raw) userList = JSON.parse(raw);
    } catch(e) {}

    const users = [];
    for (const username of userList) {
        try {
            const raw = await env.USERS.get(username);
            if (raw) {
                const u = JSON.parse(raw);
                users.push({
                    username: u.username,
                    points: u.points || 0,
                    is_banned: !!u.is_banned
                });
            }
        } catch(e) {}
    }

    return new Response(JSON.stringify(users), { status: 200, headers: getCorsHeaders(request) });
}

async function handleAdminUserAction(request, env) {
    const secret = request.headers.get('X-Admin-Secret');
    if (secret !== env.ADMIN_SECRET) return new Response(JSON.stringify({ error: 'Unauthorized' }), { status: 403, headers: getCorsHeaders(request) });
    const { username, action, value } = await request.json();
    if (!username || !action) return new Response(JSON.stringify({ error: 'Missing args' }), { status: 400, headers: getCorsHeaders(request) });

    const userJson = await env.USERS.get(username);
    if (!userJson) return new Response(JSON.stringify({ error: 'User not found' }), { status: 404, headers: getCorsHeaders(request) });

    const user = JSON.parse(userJson);

    if (action === 'ban') {
        user.is_banned = true;
    } else if (action === 'unban') {
        user.is_banned = false;
    } else if (action === 'edit_balance') {
        const p = parseInt(value);
        if(!isNaN(p)) {
            user.points = p;
        }
    } else if (action === 'delete') {
        await env.USERS.delete(username);
        let userList = [];
        try {
            const raw = await env.USERS.get('SYSTEM:USER_LIST');
            if (raw) userList = JSON.parse(raw);
            userList = userList.filter(u => u !== username);
            await env.USERS.put('SYSTEM:USER_LIST', JSON.stringify(userList));
        } catch(e) {}
        return new Response(JSON.stringify({ message: 'Deleted' }), { status: 200, headers: getCorsHeaders(request) });
    }

    await env.USERS.put(username, JSON.stringify(user));
    return new Response(JSON.stringify({ message: 'Action completed', user }), { status: 200, headers: getCorsHeaders(request) });
}

async function handleAdminWithdrawals(request, env) {
    const secret = request.headers.get('X-Admin-Secret');
    if (secret !== env.ADMIN_SECRET) return new Response(JSON.stringify({ error: 'Unauthorized' }), { status: 403, headers: getCorsHeaders(request) });
    let withdrawals = [];
    try {
        const raw = await env.USERS.get('SYSTEM:WITHDRAWALS');
        if (raw) withdrawals = JSON.parse(raw);
    } catch(e) {}

    // Sort newest first
    withdrawals.sort((a,b) => b.timestamp - a.timestamp);

    return new Response(JSON.stringify(withdrawals), { status: 200, headers: getCorsHeaders(request) });
}

async function handleAdminWithdrawalAction(request, env) {
    const secret = request.headers.get('X-Admin-Secret');
    if (secret !== env.ADMIN_SECRET) return new Response(JSON.stringify({ error: 'Unauthorized' }), { status: 403, headers: getCorsHeaders(request) });
    const { id, action } = await request.json();
    if (!id || !action) return new Response(JSON.stringify({ error: 'Missing args' }), { status: 400, headers: getCorsHeaders(request) });

    let withdrawals = [];
    try {
        const raw = await env.USERS.get('SYSTEM:WITHDRAWALS');
        if (raw) withdrawals = JSON.parse(raw);
    } catch(e) {}

    const w = withdrawals.find(x => x.id === id);
    if (!w) return new Response(JSON.stringify({ error: 'Not found' }), { status: 404, headers: getCorsHeaders(request) });

    if (action === 'paid') {
        w.status = 'paid';
    }

    await env.USERS.put('SYSTEM:WITHDRAWALS', JSON.stringify(withdrawals));
    return new Response(JSON.stringify({ message: 'Action completed' }), { status: 200, headers: getCorsHeaders(request) });
}
