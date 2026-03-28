import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

class AppShell extends StatelessWidget {
  final Widget child;

  const AppShell({super.key, required this.child});

  int _calculateSelectedIndex(BuildContext context) {
    final String location = GoRouterState.of(context).uri.path;
    if (location.startsWith('/files')) return 1;
    if (location.startsWith('/calendar')) return 2;
    return 0; // Default to Chat
  }

  void _onItemTapped(int index, BuildContext context) {
    final path = switch (index) {
      0 => '/chat',
      1 => '/files',
      2 => '/calendar',
      _ => '/chat',
    };
    context.go(path);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: child,
      bottomNavigationBar: NavigationBar(
        selectedIndex: _calculateSelectedIndex(context),
        onDestinationSelected: (index) => _onItemTapped(index, context),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.chat_bubble_outline),
            selectedIcon: Icon(Icons.chat_bubble),
            label: 'Chat',
            tooltip: 'Navigate to Chat tab',
          ),
          NavigationDestination(
            icon: Icon(Icons.folder_open),
            selectedIcon: Icon(Icons.folder),
            label: 'Files',
            tooltip: 'Navigate to Files tab',
          ),
          NavigationDestination(
            icon: Icon(Icons.calendar_today),
            selectedIcon: Icon(Icons.calendar_month),
            label: 'Calendar',
            tooltip: 'Navigate to Calendar tab',
          ),
        ],
      ),
    );
  }
}