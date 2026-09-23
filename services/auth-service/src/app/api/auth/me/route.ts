import 'reflect-metadata';
import { NextResponse } from 'next/server';
import { findUserById } from '../../../../lib/store';
import { withAuth } from '../../../../lib/guard';

export const GET = withAuth(async (_req: Request, payload) => {
  const user = await findUserById(payload.sub);
  if (!user) {
    return NextResponse.json({ errorCode: 'AUTH-401', message: 'Unauthorised' }, { status: 401 });
  }

  return NextResponse.json({ id: user.id, username: user.username, accountId: user.accountId, roles: user.roles, createdOn: user.createdOn }, { status: 200 });
});

