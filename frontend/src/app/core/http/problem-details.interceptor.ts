import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { ProblemDetails } from './problem-details';

export const problemDetailsInterceptor: HttpInterceptorFn = (request, next) =>
  next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      const problem = error.error as ProblemDetails | undefined;
      return throwError(() => problem ?? error);
    }),
  );
