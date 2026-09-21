import { NextResponse } from 'next/server';

export function invalidInput() {
  return NextResponse.json(
    {
      errorCode: 'VAL-422',
      message: 'Invalid input',
    },
    { status: 422 },
  );
}

export function unauthorised() {
  return NextResponse.json(
    {
      errorCode: 'AUTH-401',
      message: 'Unauthorised',
    },
    { status: 401 },
  );
}

export function registrationFailed() {
  return NextResponse.json(
    {
      errorCode: 'AUTH-409',
      message: 'Registration failed',
    },
    { status: 409 },
  );
}