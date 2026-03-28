import 'package:flutter/material.dart';
import 'package:weave/core/router/app_router.dart';
import 'package:weave/core/theme/app_theme.dart';

void main() => runApp(const WeaveApp());

/// Root widget for the Weave collaboration app.
class WeaveApp extends StatelessWidget {
  const WeaveApp({super.key});

  @override
  Widget build(BuildContext context) => MaterialApp.router(
        title: 'Weave',
        debugShowCheckedModeBanner: false,
        theme: AppTheme.light,
        darkTheme: AppTheme.dark,
        routerConfig: appRouter,
      );
}
