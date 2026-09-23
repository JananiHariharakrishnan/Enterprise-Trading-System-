import 'reflect-metadata';
import { NextResponse } from 'next/server';
import { findUserById } from '../../../../lib/store';
import { verifyAuthorizationHeader } from '../../../../lib/guard';

export async function GET(req: Request) {
  const auth = req.headers.get('authorization');
  const verified = verifyAuthorizationHeader(auth);

  if (!verified.ok) {
    return NextResponse.json({ errorCode: 'AUTH-401', message: 'Unauthorised' }, { status: 401 });
  }

  const payload = verified.payload as any;
  const user = await findUserById(payload.sub as string);
  if (!user) {
    return NextResponse.json({ errorCode: 'AUTH-401', message: 'Unauthorised' }, { status: 401 });
  }

  return NextResponse.json({ id: user.id, username: user.username, accountId: user.accountId, roles: user.roles, createdOn: user.createdOn }, { status: 200 });
}

