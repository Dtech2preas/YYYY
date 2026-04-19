// Simple Backend Logic using ACC_KV for users and points

function getCorsHeaders(request) {
  return {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
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
      } else if (path === '/user' && request.method === 'GET') {
        return await handleGetUser(request, env);
      } else if (path === '/add-point' && request.method === 'POST') {
        return await handleAddPoint(request, env);
      } else if (path === '/admin/users' && request.method === 'GET') {
        return await handleAdminUsers(request, env);
      } else if (path === '/admin/action' && request.method === 'POST') {
        return await handleAdminAction(request, env);
      } else {
        return new Response('Not Found', { status: 404, headers: getCorsHeaders(request) });
      }
    } catch (err) {
      return new Response(JSON.stringify({ error: err.message }), { status: 500, headers: getCorsHeaders(request) });
    }
  },
};

// --- Handlers ---

async function handleRegister(request, env) {
  const { username, password } = await request.json();
  if (!username || !password) {
    return new Response(JSON.stringify({ error: 'Missing username or password' }), { status: 400, headers: getCorsHeaders(request) });
  }

  const existing = await env.ACC_KV.get(`user:${username}`);
  if (existing) {
    return new Response(JSON.stringify({ error: 'Username already taken' }), { status: 409, headers: getCorsHeaders(request) });
  }

  const user = {
    username,
    password, // Storing in plain text for simplicity as requested, no complex logic
    points: 0,
    is_banned: false
  };

  await env.ACC_KV.put(`user:${username}`, JSON.stringify(user));

  // Add to user index for admin panel
  let userList = await env.ACC_KV.get('user_list');
  userList = userList ? JSON.parse(userList) : [];
  if (!userList.includes(username)) {
      userList.push(username);
      await env.ACC_KV.put('user_list', JSON.stringify(userList));
  }

  return new Response(JSON.stringify({ message: 'User registered successfully' }), { status: 201, headers: getCorsHeaders(request) });
}

async function handleLogin(request, env) {
  const { username, password } = await request.json();
  if (!username || !password) {
    return new Response(JSON.stringify({ error: 'Missing username or password' }), { status: 400, headers: getCorsHeaders(request) });
  }

  const userJson = await env.ACC_KV.get(`user:${username}`);
  if (!userJson) {
    return new Response(JSON.stringify({ error: 'User not found' }), { status: 404, headers: getCorsHeaders(request) });
  }

  const user = JSON.parse(userJson);
  if (user.password !== password) {
    return new Response(JSON.stringify({ error: 'Invalid credentials' }), { status: 401, headers: getCorsHeaders(request) });
  }

  if (user.is_banned) {
    return new Response(JSON.stringify({ error: 'Account is banned' }), { status: 403, headers: getCorsHeaders(request) });
  }

  return new Response(JSON.stringify({
    message: 'Login successful',
    username: user.username,
    points: user.points
  }), { status: 200, headers: getCorsHeaders(request) });
}

async function handleGetUser(request, env) {
  const url = new URL(request.url);
  const username = url.searchParams.get('username');
  if (!username) {
    return new Response(JSON.stringify({ error: 'Missing username' }), { status: 400, headers: getCorsHeaders(request) });
  }

  const userJson = await env.ACC_KV.get(`user:${username}`);
  if (!userJson) {
    return new Response(JSON.stringify({ error: 'User not found' }), { status: 404, headers: getCorsHeaders(request) });
  }

  const user = JSON.parse(userJson);

  if (user.is_banned) {
    return new Response(JSON.stringify({ error: 'Account is banned' }), { status: 403, headers: getCorsHeaders(request) });
  }

  return new Response(JSON.stringify({
    username: user.username,
    points: user.points
  }), { status: 200, headers: getCorsHeaders(request) });
}

async function handleAddPoint(request, env) {
  const { username } = await request.json();
  if (!username) {
    return new Response(JSON.stringify({ error: 'Missing username' }), { status: 400, headers: getCorsHeaders(request) });
  }

  const userJson = await env.ACC_KV.get(`user:${username}`);
  if (!userJson) {
    return new Response(JSON.stringify({ error: 'User not found' }), { status: 404, headers: getCorsHeaders(request) });
  }

  const user = JSON.parse(userJson);

  if (user.is_banned) {
    return new Response(JSON.stringify({ error: 'Account is banned' }), { status: 403, headers: getCorsHeaders(request) });
  }

  user.points += 1;
  await env.ACC_KV.put(`user:${username}`, JSON.stringify(user));

  return new Response(JSON.stringify({
    message: 'Point added',
    points: user.points
  }), { status: 200, headers: getCorsHeaders(request) });
}

async function handleAdminUsers(request, env) {
  let userListJson = await env.ACC_KV.get('user_list');
  let usernames = userListJson ? JSON.parse(userListJson) : [];

  let users = [];
  for (const username of usernames) {
    const userJson = await env.ACC_KV.get(`user:${username}`);
    if (userJson) {
      const u = JSON.parse(userJson);
      users.push({
        username: u.username,
        points: u.points,
        is_banned: u.is_banned || false
      });
    }
  }

  return new Response(JSON.stringify(users), { status: 200, headers: getCorsHeaders(request) });
}

async function handleAdminAction(request, env) {
  const { username, action, value } = await request.json();
  if (!username || !action) {
    return new Response(JSON.stringify({ error: 'Missing username or action' }), { status: 400, headers: getCorsHeaders(request) });
  }

  const userJson = await env.ACC_KV.get(`user:${username}`);

  if (action === 'delete') {
      if (userJson) {
          await env.ACC_KV.delete(`user:${username}`);
      }
      let userListJson = await env.ACC_KV.get('user_list');
      if (userListJson) {
          let usernames = JSON.parse(userListJson);
          usernames = usernames.filter(u => u !== username);
          await env.ACC_KV.put('user_list', JSON.stringify(usernames));
      }
      return new Response(JSON.stringify({ message: 'User deleted' }), { status: 200, headers: getCorsHeaders(request) });
  }

  if (!userJson) {
    return new Response(JSON.stringify({ error: 'User not found' }), { status: 404, headers: getCorsHeaders(request) });
  }

  const user = JSON.parse(userJson);

  if (action === 'ban') {
    user.is_banned = true;
  } else if (action === 'unban') {
    user.is_banned = false;
  } else if (action === 'edit_balance') {
    const points = parseInt(value);
    if (isNaN(points)) {
      return new Response(JSON.stringify({ error: 'Invalid points value' }), { status: 400, headers: getCorsHeaders(request) });
    }
    user.points = points;
  } else {
      return new Response(JSON.stringify({ error: 'Invalid action' }), { status: 400, headers: getCorsHeaders(request) });
  }

  await env.ACC_KV.put(`user:${username}`, JSON.stringify(user));
  return new Response(JSON.stringify({ message: 'Action successful' }), { status: 200, headers: getCorsHeaders(request) });
}
