import 'reflect-metadata';
import { NextResponse } from 'next/server';
import { verifyAccessToken } from '../../../../lib/jwt';
import { findUserById } from '../../../../lib/store';

export async function GET(req: Request) {
  try {
    const auth = req.headers.get('authorization') || '';
    if (!auth.toLowerCase().startsWith('bearer ')) {
      return NextResponse.json({ errorCode: 'AUTH-401', message: 'Unauthorised' }, { status: 401 });
    }
    const token = auth.slice(7).trim();
    const result = verifyAccessToken(token);
    if (!result.valid) {
      return NextResponse.json({ errorCode: 'AUTH-401', message: 'Unauthorised' }, { status: 401 });
    }

    const payload: any = result.payload;
    // Ensure required claims present
    if (!payload.sub || !payload.accountId || !payload.roles) {
      return NextResponse.json({ errorCode: 'AUTH-401', message: 'Unauthorised' }, { status: 401 });
    }

    const user = findUserById(payload.sub as string);
    if (!user) {
      return NextResponse.json({ errorCode: 'AUTH-401', message: 'Unauthorised' }, { status: 401 });
    }

    return NextResponse.json({ id: user.id, username: user.username, accountId: user.accountId, roles: user.roles, createdOn: user.createdOn }, { status: 200 });
  } catch (err) {
    return NextResponse.json({ errorCode: 'AUTH-401', message: 'Unauthorised' }, { status: 401 });
  }
}

