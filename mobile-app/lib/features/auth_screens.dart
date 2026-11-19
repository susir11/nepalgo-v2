import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../core/providers.dart';
import '../core/push.dart' show FirebaseSetup;
import '../core/theme.dart';
import '../core/widgets.dart';

class _AuthScaffold extends StatelessWidget {
  final String title, subtitle;
  final List<Widget> children;
  const _AuthScaffold({required this.title, required this.subtitle, required this.children});
  @override
  Widget build(BuildContext context) => Scaffold(
        body: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(20),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 440),
                child: NgCard(
                  padding: const EdgeInsets.all(24),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                    Text(title, style: Theme.of(context).textTheme.displaySmall),
                    const SizedBox(height: 6),
                    Text(subtitle, style: Theme.of(context).textTheme.bodySmall),
                    const SizedBox(height: 20),
                    ...children,
                  ]),
                ),
              ),
            ),
          ),
        ),
      );
}

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});
  @override
  ConsumerState<LoginScreen> createState() => _LoginState();
}

class _LoginState extends ConsumerState<LoginScreen> {
  final _form = GlobalKey<FormState>();
  final _email = TextEditingController(), _password = TextEditingController();
  bool _busy = false;

  Future<void> _submit() async {
    if (!_form.currentState!.validate()) return;
    setState(() => _busy = true);
    try {
      await ref.read(authProvider.notifier).signIn(_email.text.trim(), _password.text);
    } catch (e) {
      if (mounted) showError(context, e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => _AuthScaffold(
        title: 'Welcome back',
        subtitle: 'Sign in to plan and manage your Nepal trips.',
        children: [
          Form(
            key: _form,
            child: AutofillGroup(
              child: Column(children: [
                TextFormField(controller: _email, keyboardType: TextInputType.emailAddress, autofillHints: const [AutofillHints.email],
                    decoration: const InputDecoration(labelText: 'Email'), validator: _emailValidator),
                const SizedBox(height: 12),
                TextFormField(controller: _password, obscureText: true, autofillHints: const [AutofillHints.password],
                    decoration: const InputDecoration(labelText: 'Password'), onFieldSubmitted: (_) => _submit(),
                    validator: (v) => (v == null || v.isEmpty) ? 'Enter your password' : null),
              ]),
            ),
          ),
          Align(alignment: Alignment.centerRight, child: TextButton(onPressed: () => context.push('/forgot'), child: const Text('Forgot password?'))),
          const SizedBox(height: 4),
          FilledButton(onPressed: _busy ? null : _submit, child: _busy ? const _Spinner() : const Text('Sign in')),
          const SizedBox(height: 16),
          const _SocialSignIn(),
          const SizedBox(height: 8),
          TextButton(onPressed: () => context.go('/signup'), child: const Text('New to NepalGo? Create an account')),
        ],
      );
}

class SignUpScreen extends ConsumerStatefulWidget {
  const SignUpScreen({super.key});
  @override
  ConsumerState<SignUpScreen> createState() => _SignUpState();
}

class _SignUpState extends ConsumerState<SignUpScreen> {
  final _form = GlobalKey<FormState>();
  final _name = TextEditingController(), _email = TextEditingController(), _password = TextEditingController();
  bool _busy = false, _adult = false;

  Future<void> _submit() async {
    if (!_form.currentState!.validate()) return;
    if (!_adult) {
      toast(context, 'You must be 18 or older to use NepalGo (Terms §1).');
      return;
    }
    setState(() => _busy = true);
    try {
      await ref.read(authProvider.notifier).signUp(_name.text.trim(), _email.text.trim(), _password.text);
    } catch (e) {
      if (mounted) showError(context, e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => _AuthScaffold(
        title: 'Create account',
        subtitle: 'You\'ll get a permanent NepalGo ID — share it with our team on WhatsApp so we can find your account.',
        children: [
          Form(
            key: _form,
            child: Column(children: [
              TextFormField(controller: _name, decoration: const InputDecoration(labelText: 'Your name'), autofillHints: const [AutofillHints.name],
                  validator: (v) => (v == null || v.trim().isEmpty) ? 'Enter your name' : null),
              const SizedBox(height: 12),
              TextFormField(controller: _email, keyboardType: TextInputType.emailAddress, decoration: const InputDecoration(labelText: 'Email'),
                  validator: _emailValidator),
              const SizedBox(height: 12),
              TextFormField(controller: _password, obscureText: true, decoration: const InputDecoration(labelText: 'Password', helperText: 'At least 8 characters'),
                  validator: (v) => (v == null || v.length < 8) ? 'Use at least 8 characters' : null),
            ]),
          ),
          const SizedBox(height: 8),
          CheckboxListTile(contentPadding: EdgeInsets.zero, value: _adult, onChanged: (v) => setState(() => _adult = v ?? false),
              controlAffinity: ListTileControlAffinity.leading, title: const Text('I am 18 or older')),
          FilledButton(onPressed: _busy ? null : _submit, child: _busy ? const _Spinner() : const Text('Create account')),
          const SizedBox(height: 8),
          TextButton(onPressed: () => context.go('/login'), child: const Text('Already have an account? Sign in')),
        ],
      );
}

class ForgotPasswordScreen extends ConsumerStatefulWidget {
  const ForgotPasswordScreen({super.key});
  @override
  ConsumerState<ForgotPasswordScreen> createState() => _ForgotState();
}

class _ForgotState extends ConsumerState<ForgotPasswordScreen> {
  final _email = TextEditingController();
  bool _busy = false, _sent = false;

  @override
  Widget build(BuildContext context) => _AuthScaffold(
        title: 'Reset password',
        subtitle: _sent
            ? 'If an account exists for that email, a reset link is on its way. Check your inbox (and spam).'
            : 'We\'ll email you a link to choose a new password.',
        children: [
          if (!_sent) ...[
            TextField(controller: _email, keyboardType: TextInputType.emailAddress, decoration: const InputDecoration(labelText: 'Email')),
            const SizedBox(height: 16),
            FilledButton(
              onPressed: _busy
                  ? null
                  : () async {
                      if (_emailValidator(_email.text) != null) return toast(context, 'Enter a valid email');
                      setState(() => _busy = true);
                      try {
                        await ref.read(authProvider.notifier).sendPasswordReset(_email.text.trim());
                      } catch (_) {/* same message either way: never reveal whether an email has an account */}
                      if (mounted) setState(() { _busy = false; _sent = true; });
                    },
              child: _busy ? const _Spinner() : const Text('Send reset link'),
            ),
          ],
          TextButton(onPressed: () => context.go('/login'), child: const Text('Back to sign in')),
        ],
      );
}

// Social sign-in (spec 5). Google is live when the build has the FlutterFire
// config (google-services.json / GoogleService-Info.plist). Apple + Facebook
// need their developer accounts first. Apple MUST ship on iOS alongside Google
// (App Store guideline 4.8) before an iOS release.
class _SocialSignIn extends ConsumerStatefulWidget {
  const _SocialSignIn();
  @override
  ConsumerState<_SocialSignIn> createState() => _SocialSignInState();
}

class _SocialSignInState extends ConsumerState<_SocialSignIn> {
  bool _busy = false;

  Future<void> _google() async {
    setState(() => _busy = true);
    try {
      await ref.read(authProvider.notifier).signInWithGoogle();   // false = user closed the sheet
    } catch (e) {
      if (mounted) showError(context, e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final note = Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: context.ng.primaryTint, borderRadius: BorderRadius.circular(NgRadius.inner)),
      child: Text(FirebaseSetup.ready ? 'Apple and Facebook sign-in coming soon.' : 'Google, Apple and Facebook sign-in coming soon.',
          textAlign: TextAlign.center, style: TextStyle(color: context.ng.primary, fontSize: 13)),
    );
    if (!FirebaseSetup.ready) return note;
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      Row(children: [
        const Expanded(child: Divider()),
        Padding(padding: const EdgeInsets.symmetric(horizontal: 12), child: Text('or', style: Theme.of(context).textTheme.bodySmall)),
        const Expanded(child: Divider()),
      ]),
      const SizedBox(height: 12),
      OutlinedButton.icon(
        onPressed: _busy ? null : _google,
        icon: _busy
            ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
            : const Text('G', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 18, color: Color(0xFF4285F4))),
        label: const Text('Continue with Google'),
      ),
      const SizedBox(height: 12),
      note,
    ]);
  }
}

class _Spinner extends StatelessWidget {
  const _Spinner();
  @override
  Widget build(BuildContext context) => const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white));
}

String? _emailValidator(String? v) => (v == null || !RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch(v.trim())) ? 'Enter a valid email' : null;
